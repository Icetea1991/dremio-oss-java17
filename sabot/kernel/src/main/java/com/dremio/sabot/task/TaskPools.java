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
package com.dremio.sabot.task;

import com.dremio.common.config.SabotConfig;
import com.dremio.sabot.task.single.DedicatedTaskPool;

/**
 * Task pool utilities
 */
public final class TaskPools {

  public static final String DREMIO_TASK_POOL_FACTORY_CLASS = "dremio.task.pool.factory.class";

  private TaskPools() {}

  public static TaskPoolFactory newFactory(SabotConfig config) {
    final TaskPoolFactory factory;
    if (config.hasPath(TaskPools.DREMIO_TASK_POOL_FACTORY_CLASS)) {
      factory = config.getInstance(TaskPools.DREMIO_TASK_POOL_FACTORY_CLASS, TaskPoolFactory.class);
    } else {
      factory = new DedicatedTaskPool.Factory();
    }

    return factory;
  }
}
