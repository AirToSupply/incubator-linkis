/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.apache.linkis.metadatamanager.server.service.impl;

import org.apache.linkis.common.exception.ErrorException;
import org.apache.linkis.datasourcemanager.common.util.json.Json;
import org.apache.linkis.datasourcemanager.common.protocol.DsInfoQueryRequest;
import org.apache.linkis.datasourcemanager.common.protocol.DsInfoResponse;
import org.apache.linkis.metadatamanager.common.MdmConfiguration;
import org.apache.linkis.metadatamanager.common.domain.MetaColumnInfo;
import org.apache.linkis.metadatamanager.common.domain.MetaPartitionInfo;
import org.apache.linkis.metadatamanager.server.service.MetadataAppService;
import org.apache.linkis.metadatamanager.common.protocol.*;
import org.apache.linkis.rpc.Sender;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Service
public class MetadataAppServiceImpl implements MetadataAppService {
    private Sender dataSourceRpcSender;
    private MetaClassLoaderManager metaClassLoaderManager;

    @PostConstruct
    public void init(){
        dataSourceRpcSender = Sender.getSender(MdmConfiguration.DATA_SOURCE_SERVICE_APPLICATION.getValue());
        metaClassLoaderManager = new MetaClassLoaderManager();
    }


    @Override
    public void getConnection(String dataSourceType, String operator, Map<String, Object> params) throws Exception {
        BiFunction<String, Object[], Object> invoker = metaClassLoaderManager.getInvoker(dataSourceType);
        invoker.apply("getConnection", new Object[]{operator, params});
    }

    @Override
    public List<String> getDatabasesByDsId(String dataSourceId, String system) throws ErrorException {
        DsInfoResponse dsInfoResponse = reqToGetDataSourceInfo(dataSourceId, system);
        if(StringUtils.isNotBlank(dsInfoResponse.dsType())){
            BiFunction<String, Object[], Object> invoker = metaClassLoaderManager.getInvoker(dsInfoResponse.dsType());
            return (List<String>)invoker.apply("getDatabases", new Object[]{dsInfoResponse.creator(), dsInfoResponse.params()});
        }
        return new ArrayList<>();
    }

    @Override
    public List<String> getTablesByDsId(String dataSourceId, String database, String system) throws ErrorException {
        DsInfoResponse dsInfoResponse = reqToGetDataSourceInfo(dataSourceId, system);
        if(StringUtils.isNotBlank(dsInfoResponse.dsType())){
            BiFunction<String, Object[], Object> invoker = metaClassLoaderManager.getInvoker(dsInfoResponse.dsType());
            return (List<String>)invoker.apply("getTables", new Object[]{dsInfoResponse.creator(), dsInfoResponse.params(), database});
        }
        return new ArrayList<>();
    }

    @Override
    public Map<String, String> getTablePropsByDsId(String dataSourceId, String database, String table, String system) throws ErrorException {
        DsInfoResponse dsInfoResponse = reqToGetDataSourceInfo(dataSourceId, system);
        if(StringUtils.isNotBlank(dsInfoResponse.dsType())){
            BiFunction<String, Object[], Object> invoker = metaClassLoaderManager.getInvoker(dsInfoResponse.dsType());
            return (Map<String, String>)invoker.apply("getTableProps", new Object[]{dsInfoResponse.creator(), dsInfoResponse.params(), database, table});
        }
        return new HashMap<>();
    }

    @Override
    public MetaPartitionInfo getPartitionsByDsId(String dataSourceId, String database, String table, String system) throws ErrorException {
        DsInfoResponse dsInfoResponse = reqToGetDataSourceInfo(dataSourceId, system);
        if(StringUtils.isNotBlank(dsInfoResponse.dsType())){
            BiFunction<String, Object[], Object> invoker = metaClassLoaderManager.getInvoker(dsInfoResponse.dsType());
            Object partitions = invoker.apply("getPartitions", new Object[]{dsInfoResponse.creator(), dsInfoResponse.params(), database, table});
            try {
                String partitionsJson = BDPJettyServerHelper.jacksonJson().writeValueAsString(partitions);
                return BDPJettyServerHelper.jacksonJson().readValue(partitionsJson,MetaPartitionInfo.class);
            }catch (Exception e){
                throw new ErrorException(-1, "Partitions Error msg:"+e.getMessage());
            }
        }
        return new MetaPartitionInfo();
    }

    @Override
    public List<MetaColumnInfo> getColumns(String dataSourceId, String database, String table, String system) throws ErrorException {
        DsInfoResponse dsInfoResponse = reqToGetDataSourceInfo(dataSourceId, system);
        if(StringUtils.isNotBlank(dsInfoResponse.dsType())){
            BiFunction<String, Object[], Object> invoker = metaClassLoaderManager.getInvoker(dsInfoResponse.dsType());
            return (List<MetaColumnInfo>)invoker.apply("getColumns", new Object[]{dsInfoResponse.creator(), dsInfoResponse.params(), database, table});
        }
        return new ArrayList<>();
    }

    /**
     * Request to get data source information
     * (type and connection parameters)
     * @param dataSourceId data source id
     * @param system system
     * @return
     * @throws ErrorException
     */
    public DsInfoResponse reqToGetDataSourceInfo(String dataSourceId, String system) throws ErrorException{
        Object rpcResult = null;
        try {
            rpcResult = dataSourceRpcSender.ask(new DsInfoQueryRequest(dataSourceId, system));
        }catch(Exception e){
            throw new ErrorException(-1, "Remote Service Error[远端服务出错, 联系运维处理]");
        }
        if(rpcResult instanceof DsInfoResponse){
            DsInfoResponse response = (DsInfoResponse)rpcResult;
            if(!response.status()){
                throw new ErrorException(-1, "Error in Data Source Manager Server[数据源服务出错]");
            }
            return response;
        }else{
            throw new ErrorException(-1, "Remote Service Error[远端服务出错, 联系运维处理]");
        }
    }

}
