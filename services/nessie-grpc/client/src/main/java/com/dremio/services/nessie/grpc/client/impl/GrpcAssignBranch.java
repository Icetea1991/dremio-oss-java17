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
package com.dremio.services.nessie.grpc.client.impl;

import org.projectnessie.client.api.AssignBranchBuilder;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.Reference;

import com.dremio.services.nessie.grpc.api.ReferenceType;
import com.dremio.services.nessie.grpc.api.TreeServiceGrpc.TreeServiceBlockingStub;

final class GrpcAssignBranch extends GrpcAssignReference implements AssignBranchBuilder {

  GrpcAssignBranch(TreeServiceBlockingStub stub) {
    super(stub);
  }

  @Override
  public AssignBranchBuilder assignTo(Reference assignTo) {
    setAssignTo(assignTo);
    return this;
  }

  @Override
  public AssignBranchBuilder branchName(String tagName) {
    setRefName(tagName);
    return this;
  }

  @Override
  public AssignBranchBuilder hash(String hash) {
    setHash(hash);
    return this;
  }

  @Override
  public void assign() throws NessieNotFoundException, NessieConflictException {
    assignAndGet();
  }

  @Override
  public Branch assignAndGet() throws NessieNotFoundException, NessieConflictException {
    return (Branch) super.assign(ReferenceType.BRANCH);
  }
}
