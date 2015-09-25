/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.connector.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.sqoop.common.MapContext;
import org.apache.sqoop.common.SqoopException;
import org.apache.sqoop.connector.hdfs.configuration.FromJobConfiguration;
import org.apache.sqoop.connector.hdfs.configuration.IncrementalType;
import org.apache.sqoop.connector.hdfs.configuration.LinkConfiguration;
import org.apache.sqoop.error.code.HdfsConnectorError;
import org.apache.sqoop.job.etl.Initializer;
import org.apache.sqoop.job.etl.InitializerContext;
import org.apache.log4j.Logger;

import java.io.IOException;


public class HdfsFromInitializer extends Initializer<LinkConfiguration, FromJobConfiguration> {

  public static final Logger LOG = Logger.getLogger(HdfsFromInitializer.class);

  /**
   * Initialize new submission based on given configuration properties. Any
   * needed temporary values might be saved to context object and they will be
   * promoted to all other part of the workflow automatically.
   *
   * @param context Initializer context object
   * @param linkConfig link configuration object
   * @param jobConfig FROM job configuration object
   */
  @Override
  public void initialize(InitializerContext context, LinkConfiguration linkConfig, FromJobConfiguration jobConfig) {
    assert jobConfig.incremental != null;

    Configuration configuration = HdfsUtils.createConfiguration(linkConfig);
    HdfsUtils.contextToConfiguration(new MapContext(linkConfig.linkConfig.configOverrides), configuration);
    HdfsUtils.configurationToContext(configuration, context.getContext());

    boolean incremental = jobConfig.incremental.incrementalType != null && jobConfig.incremental.incrementalType == IncrementalType.NEW_FILES;

    // In case of incremental import, we need to persist the highest last modified
    try {
      FileSystem fs = FileSystem.get(configuration);
      Path path = new Path(jobConfig.fromJobConfig.inputDirectory);
      LOG.info("Input directory: " + path.toString());

      if(!fs.exists(path)) {
        throw new SqoopException(HdfsConnectorError.GENERIC_HDFS_CONNECTOR_0007, "Input directory doesn't exists");
      }

      if(fs.isFile(path)) {
        throw new SqoopException(HdfsConnectorError.GENERIC_HDFS_CONNECTOR_0007, "Input directory is a file");
      }

      if(incremental) {
        LOG.info("Detected incremental import");
        long maxModifiedTime = -1;
        FileStatus[] fileStatuses = fs.listStatus(path);
        for(FileStatus status : fileStatuses) {
          if(maxModifiedTime < status.getModificationTime()) {
            maxModifiedTime = status.getModificationTime();
          }
        }

        LOG.info("Maximal age of file is: " + maxModifiedTime);
        context.getContext().setLong(HdfsConstants.MAX_IMPORT_DATE, maxModifiedTime);
      }
    } catch (IOException e) {
      throw new SqoopException(HdfsConnectorError.GENERIC_HDFS_CONNECTOR_0007, "Unexpected exception", e);
    }

  }
}
