/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.service.nessie.upgrade.storage;

import static com.dremio.service.nessie.upgrade.storage.MigrateToNessieAdapter.MAX_ENTRIES_PER_COMMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.versioned.CommitMetaSerializer.METADATA_SERIALIZER;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.GetNamedRefsParams;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.ImmutablePut;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.ReferenceInfo;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.persist.adapter.CommitLogEntry;
import org.projectnessie.versioned.persist.adapter.ContentId;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.adapter.ImmutableCommitParams;
import org.projectnessie.versioned.persist.adapter.KeyWithBytes;
import org.projectnessie.versioned.persist.nontx.ImmutableAdjustableNonTransactionalDatabaseAdapterConfig;
import org.projectnessie.versioned.persist.nontx.NonTransactionalDatabaseAdapterConfig;
import org.projectnessie.versioned.persist.store.PersistVersionStore;

import com.dremio.common.SuppressForbidden;
import com.dremio.datastore.LocalKVStoreProvider;
import com.dremio.service.nessie.DatastoreDatabaseAdapterFactory;
import com.dremio.service.nessie.ImmutableDatastoreDbConfig;
import com.dremio.service.nessie.NessieDatastoreInstance;

/**
 * Unit tests for {@link MigrateIcebergMetadataPointer}.
 */
class TestMigrateIcebergMetadataPointer extends AbstractNessieUpgradeTest {

  // This legacy data was produced using Nessie 0.14 code.
  // The data encodes IcebergTable.of("test-metadata-location", "test-id-data", "test-content-id")
  private static final String LEGACY_REF_STATE_BASE64 = "ChgKFnRlc3QtbWV0YWRhdGEtbG9jYXRpb24qD3Rlc3QtY29udGVudC1pZA==";

  private static final String UPGRADE_BRANCH_NAME = "upgrade-test";

  private final MigrateIcebergMetadataPointer task = new MigrateIcebergMetadataPointer();
  private LocalKVStoreProvider storeProvider;
  private DatabaseAdapter adapter;

  @BeforeEach
  void createKVStore() throws Exception {
    storeProvider = new LocalKVStoreProvider(scanResult, null, true, false); // in-memory
    storeProvider.start();

    NessieDatastoreInstance nessieDatastore = new NessieDatastoreInstance();
    nessieDatastore.configure(new ImmutableDatastoreDbConfig.Builder()
      .setStoreProvider(() -> storeProvider)
      .build());
    nessieDatastore.initialize();

    NonTransactionalDatabaseAdapterConfig adapterCfg = ImmutableAdjustableNonTransactionalDatabaseAdapterConfig
      .builder()
      .validateNamespaces(false)
      .build();
    adapter = new DatastoreDatabaseAdapterFactory().newBuilder()
      .withConfig(adapterCfg)
      .withConnector(nessieDatastore)
      .build();
  }

  @AfterEach
  void stopKVStore() throws Exception {
    if (storeProvider != null) {
      storeProvider.close();
    }
  }

  @SuppressForbidden // This method has to use Nessie's relocated ByteString to interface with Nessie Database Adapters.
  private void commitLegacyData(ContentKey key, ContentId contentId)
    throws ReferenceNotFoundException, ReferenceConflictException {

    ByteString refState = ByteString.copyFrom(Base64.getDecoder().decode(LEGACY_REF_STATE_BASE64));

    adapter.commit(ImmutableCommitParams.builder()
      .toBranch(BranchName.of("main"))
      .commitMetaSerialized(METADATA_SERIALIZER.toBytes(CommitMeta.fromMessage("test")))
      .addPuts(KeyWithBytes.of(
        key,
        contentId,
        (byte) 1, // Iceberg Table
        refState))
      .build());
  }


  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 19, 20, 21, 40, 99, 100, 101})
  void testUpgrade(int numExtraTables) throws Exception {
    adapter.initializeRepo("main");
    // Load a legacy entry into the adapter
    List<ContentKey> keys = new ArrayList<>();
    ContentKey key1 = ContentKey.of("test", "table", "11111");
    ContentId contentId1 = ContentId.of("test-content-id");
    commitLegacyData(key1, contentId1);
    keys.add(key1);

    VersionStore versionStore = new PersistVersionStore(adapter);

    // Create some extra Iceberg tables in current Nessie format
    for (int i = 0; i < numExtraTables; i++) {
      ContentKey extraKey = ContentKey.of("test", "table", "current-" + i);
      IcebergTable table = IcebergTable.of("test-metadata-location", 1, 2, 3, 4,
        "extra-content-id-" + i);
      versionStore.commit(BranchName.of("main"), Optional.empty(), CommitMeta.fromMessage("test"),
        Collections.singletonList(ImmutablePut.builder()
          .key(extraKey)
          .valueSupplier(() -> table)
          .build()));
      keys.add(extraKey);
    }

    task.upgrade(storeProvider, UPGRADE_BRANCH_NAME);

    // Make sure the transient upgrade branch is deleted when the upgrade is over
    try (Stream<ReferenceInfo<ByteString>> refs = adapter.namedRefs(GetNamedRefsParams.DEFAULT)) {
      assertThat(refs).noneMatch(r -> r.getNamedRef().getName().equals(UPGRADE_BRANCH_NAME));
    }

    Map<ContentKey, Content> tables = versionStore.getValues(BranchName.of("main"), keys);

    assertThat(tables.keySet()).containsExactlyInAnyOrder(keys.toArray(new ContentKey[0]));
    assertThat(tables).allSatisfy((k, v) -> {
      assertThat(v).isInstanceOf(IcebergTable.class)
        .extracting("metadataLocation")
        .isEqualTo("test-metadata-location"); // encoded in LEGACY_REF_STATE_BASE64
    });

    ReferenceInfo<ByteString> main = adapter.namedRef("main", GetNamedRefsParams.DEFAULT);
    List<CommitLogEntry> mainLog;
    try (Stream<CommitLogEntry> log = adapter.commitLog(main.getHash())) {
      mainLog = log.collect(Collectors.toList());
    }

    // Each upgrade commit contains at most MAX_ENTRIES_PER_COMMIT entries
    int logSize = keys.size() / MAX_ENTRIES_PER_COMMIT
      + (keys.size() % MAX_ENTRIES_PER_COMMIT == 0 ? 0 : 1) // + 1 for the final non-full commit
      + 1; // + 1 for the empty key list commit
    assertThat(mainLog).hasSize(logSize);

    // The top-most (last) commit should have a key list.
    assertThat(mainLog.get(0).getKeyList())
      .isNotNull()
      .satisfies(l -> assertThat(l.getKeys()).isNotEmpty());

    // The second top-most (last commit with tables) log entry may or may not be "full".
    assertThat(mainLog.get(1).getPuts().size()).isLessThanOrEqualTo(MAX_ENTRIES_PER_COMMIT);
    // Deeper entries should be "full".
    assertThat(mainLog.subList(2, mainLog.size()))
      .allSatisfy(e -> assertThat(e.getPuts().size()).isEqualTo(MAX_ENTRIES_PER_COMMIT));
  }

  @Test
  void testEmptyUpgrade() throws Exception {
    task.upgrade(storeProvider, UPGRADE_BRANCH_NAME);

    ReferenceInfo<ByteString> main = adapter.namedRef("main", GetNamedRefsParams.DEFAULT);
    assertThat(main.getHash()).isEqualTo(adapter.noAncestorHash()); // no change during upgrade
  }

  @Test
  void testUnnecessaryUpgrade() throws Exception {
    IcebergTable table = IcebergTable.of("metadata1", 1, 2, 3, 4, "id123");

    adapter.initializeRepo("main");
    // Create an Iceberg table in current Nessie format
    VersionStore versionStore = new PersistVersionStore(adapter);
    Hash head = versionStore.commit(BranchName.of("main"), Optional.empty(), CommitMeta.fromMessage("test"),
      Collections.singletonList(ImmutablePut.builder()
        .key(ContentKey.of("test-key"))
        .valueSupplier(() -> table)
        .build()))
      .getCommitHash();

    task.upgrade(storeProvider, UPGRADE_BRANCH_NAME);

    ReferenceInfo<ByteString> main = adapter.namedRef("main", GetNamedRefsParams.DEFAULT);
    assertThat(main.getHash()).isEqualTo(head); // no change during upgrade
  }

  @Test
  void testUnnecessaryUpgradeOfDeletedEntry() throws Exception {
    adapter.initializeRepo("main");
    // Load a legacy entry into the adapter
    ContentKey key1 = ContentKey.of("test", "table", "11111");
    ContentId contentId1 = ContentId.of("test-content-id");
    commitLegacyData(key1, contentId1);

    // Delete the legacy entry
    Hash head = adapter.commit(ImmutableCommitParams.builder()
      .toBranch(BranchName.of("main"))
      .commitMetaSerialized(METADATA_SERIALIZER.toBytes(CommitMeta.fromMessage("test delete")))
      .addDeletes(key1)
      .build())
      .getCommitHash();

    task.upgrade(storeProvider, UPGRADE_BRANCH_NAME);

    ReferenceInfo<ByteString> main = adapter.namedRef("main", GetNamedRefsParams.DEFAULT);
    assertThat(main.getHash()).isEqualTo(head); // no change during upgrade
  }

  @Test
  void testUnnecessaryUpgradeOfReplacedEntry() throws Exception {
    adapter.initializeRepo("main");
    // Load a legacy entry into the adapter
    ContentKey key1 = ContentKey.of("test", "table", "11111");
    ContentId contentId1 = ContentId.of("test-content-id");
    commitLegacyData(key1, contentId1);

    // Replace the table using current Nessie format
    IcebergTable table = IcebergTable.of("metadata1", 1, 2, 3, 4, "id123");
    VersionStore versionStore = new PersistVersionStore(adapter);
    Hash head = versionStore.commit(BranchName.of("main"), Optional.empty(), CommitMeta.fromMessage("test"),
      Collections.singletonList(ImmutablePut.builder()
        .key(key1)
        .valueSupplier(() -> table)
        .build()))
      .getCommitHash();

    task.upgrade(storeProvider, UPGRADE_BRANCH_NAME);

    ReferenceInfo<ByteString> main = adapter.namedRef("main", GetNamedRefsParams.DEFAULT);
    assertThat(main.getHash()).isEqualTo(head); // no change during upgrade
  }

  @Test
  void testUpgradeBranchReset() throws Exception {
    adapter.initializeRepo("main");
    commitLegacyData(ContentKey.of("test1"), ContentId.of("test-cid"));

    adapter.create(BranchName.of(UPGRADE_BRANCH_NAME), adapter.noAncestorHash());
    task.upgrade(storeProvider, UPGRADE_BRANCH_NAME);

    try (Stream<ReferenceInfo<ByteString>> refs = adapter.namedRefs(GetNamedRefsParams.DEFAULT)) {
      assertThat(refs).noneMatch(r -> r.getNamedRef().getName().equals(UPGRADE_BRANCH_NAME));
    }
  }
}
