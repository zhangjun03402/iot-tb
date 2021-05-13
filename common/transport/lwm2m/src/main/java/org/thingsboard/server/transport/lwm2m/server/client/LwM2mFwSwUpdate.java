/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.server.transport.lwm2m.server.client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.leshan.core.request.ContentFormat;
import org.thingsboard.server.common.data.firmware.FirmwareType;
import org.thingsboard.server.common.data.firmware.FirmwareUpdateStatus;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.transport.lwm2m.server.DefaultLwM2MTransportMsgHandler;
import org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.thingsboard.server.common.data.firmware.FirmwareKey.STATE;
import static org.thingsboard.server.common.data.firmware.FirmwareType.FIRMWARE;
import static org.thingsboard.server.common.data.firmware.FirmwareType.SOFTWARE;
import static org.thingsboard.server.common.data.firmware.FirmwareUpdateStatus.UPDATING;
import static org.thingsboard.server.common.data.firmware.FirmwareUtil.getAttributeKey;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_NAME_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_PACKAGE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_RESULT_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_STATE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_UPDATE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_UPDATE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.FW_VER_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LOG_LW2M_INFO;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.EXECUTE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.OBSERVE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.READ;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LwM2mTypeOper.WRITE_REPLACE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_INSTALL_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_NAME_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_PACKAGE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_RESULT_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_UN_INSTALL_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_UPDATE;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_UPDATE_STATE_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.SW_VER_ID;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.convertPathFromObjectIdToIdVer;
import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.splitCamelCaseString;

@Slf4j
public class LwM2mFwSwUpdate {
    // 5/0/6 PkgName
    // 9/0/0 PkgName
    @Getter
    @Setter
    private volatile String currentTitle;
    // 5/0/7 PkgVersion
    // 9/0/1 PkgVersion
    @Getter
    @Setter
    private volatile String currentVersion;
    @Getter
    @Setter
    private volatile UUID currentId;
    @Getter
    @Setter
    private volatile String stateUpdate;
    @Getter
    private String pathPackageId;
    @Getter
    private String pathStateId;
    @Getter
    private String pathResultId;
    @Getter
    private String pathNameId;
    @Getter
    private String pathVerId;
    @Getter
    private String pathInstallId;
    @Getter
    private String pathUnInstallId;
    @Getter
    private String wUpdate;
    @Getter
    @Setter
    private volatile boolean infoFwSwUpdate = false;
    private final FirmwareType type;
    private DefaultLwM2MTransportMsgHandler serviceImpl;
    @Getter
    LwM2mClient lwM2MClient;
    @Getter
    @Setter
    private final List<String> pendingInfoRequestsStart;

    public LwM2mFwSwUpdate(LwM2mClient lwM2MClient, FirmwareType type) {
        this.lwM2MClient = lwM2MClient;
        this.pendingInfoRequestsStart = new CopyOnWriteArrayList<>();
        this.type = type;
        this.stateUpdate = null;
        this.initPathId();
    }

    private void initPathId() {
        if (this.type.equals(FIRMWARE)) {
            this.pathPackageId = FW_PACKAGE_ID;
            this.pathStateId = FW_STATE_ID;
            this.pathResultId = FW_RESULT_ID;
            this.pathNameId = FW_NAME_ID;
            this.pathVerId = FW_VER_ID;
            this.pathInstallId = FW_UPDATE_ID;
            this.wUpdate = FW_UPDATE;
        } else if (this.type.equals(SOFTWARE)) {
            this.pathPackageId = SW_PACKAGE_ID;
            this.pathStateId = SW_UPDATE_STATE_ID;
            this.pathResultId = SW_RESULT_ID;
            this.pathNameId = SW_NAME_ID;
            this.pathVerId = SW_VER_ID;
            this.pathInstallId = SW_INSTALL_ID;
            this.pathUnInstallId = SW_UN_INSTALL_ID;
            this.wUpdate = SW_UPDATE;
        }
    }

    public void initReadValue(DefaultLwM2MTransportMsgHandler serviceImpl, String pathIdVer) {
        if (this.serviceImpl == null) this.serviceImpl = serviceImpl;
        if (pathIdVer != null) {
            this.pendingInfoRequestsStart.remove(pathIdVer);
        }
        if (this.pendingInfoRequestsStart.size() == 0) {
            this.infoFwSwUpdate = false;
            if (!FirmwareUpdateStatus.DOWNLOADING.name().equals(this.stateUpdate)) {
                boolean conditionalStart = this.type.equals(FIRMWARE) ? this.conditionalFwUpdateStart() :
                        this.conditionalSwUpdateStart();
                if (conditionalStart) {
                    this.writeFwSwWare();
                }
            }
        }
    }

    /**
     * Send FsSw to Lwm2mClient:
     * before operation Write: fw_state = DOWNLOADING
     */
    private void writeFwSwWare() {
        this.stateUpdate = FirmwareUpdateStatus.DOWNLOADING.name();
        this.observeStateUpdate();
        this.sendLogs(WRITE_REPLACE.name());
        int chunkSize = 0;
        int chunk = 0;
        byte[] firmwareChunk = this.serviceImpl.firmwareDataCache.get(this.currentId.toString(), chunkSize, chunk);
        String targetIdVer = convertPathFromObjectIdToIdVer(this.pathPackageId, this.lwM2MClient.getRegistration());
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(), targetIdVer, WRITE_REPLACE, ContentFormat.OPAQUE.getName(),
                firmwareChunk, this.serviceImpl.config.getTimeout(), null);
    }

    public void sendLogs(String typeOper) {
        this.sendSateOnThingsboard();
        String msg = String.format("%s: %s, %s, pkgVer: %s: pkgName - %s.",
                LOG_LW2M_INFO, this.wUpdate, typeOper, this.currentVersion, this.currentTitle);
        serviceImpl.sendLogsToThingsboard(msg, lwM2MClient.getRegistration().getId());
        log.warn("{} state: [{}]", msg, this.stateUpdate);
    }


    /**
     * After inspection Update Result
     * fw_state/sw_state = UPDATING
     * send execute
     */
    public void executeFwSwWare() {
        boolean conditionalExecute = this.type.equals(FIRMWARE) ? conditionalFwUpdateExecute() :
                conditionalSwUpdateExecute();
        if (conditionalExecute) {
            this.setStateUpdate(UPDATING.name());
            this.sendLogs(EXECUTE.name());
            this.serviceImpl.lwM2mTransportRequest.sendAllRequest(this.lwM2MClient.getRegistration(), this.pathInstallId, EXECUTE, ContentFormat.TLV.getName(),
                    null, 0, null);
        }
    }


    /**
     * Firmware start:
     * -- Если Update Result -errors (более 1)  - Это означает что пред. апдейт не прошел.
     *  - Запускаем апдейт в независимости от состяния прошивки и ее версии.
     * -- Если Update Result - не errors (менее или равно 1) и ver не пустой  - Это означает что пред. апдейт прошел.
     * -- Если Update Result - не errors и ver  пустой  - Это означает что апдейта еще не было.
     * - Проверяем поменялась ли версия и запускаем новый апдейт.
     */
    private boolean conditionalFwUpdateStart() {
        Long updateResultFw = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        // #1/#2
        return updateResultFw > LwM2mTransportUtil.UpdateResultFw.UPDATE_SUCCESSFULLY.code ||
                (
                        (updateResultFw <= LwM2mTransportUtil.UpdateResultFw.UPDATE_SUCCESSFULLY.code
                        ) &&
                                (
                                        (this.currentVersion != null && !this.currentVersion.equals(this.lwM2MClient.getResourceValue(null, this.pathVerId))) ||
                                                (this.currentTitle != null && !this.currentTitle.equals(this.lwM2MClient.getResourceValue(null, this.pathNameId)))
                                )
                );
    }


    /**
     * Before operation Execute  inspection Update Result :
     * 0 - Initial value
     */
    private boolean conditionalFwUpdateExecute() {
        Long updateResult = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        return  LwM2mTransportUtil.UpdateResultFw.INITIAL.code == updateResult;
    }

    /**
     * Software start
     * -- Если Update Result -errors (равно и более 50)  - Это означает что пред. апдейт не прошел.
     * * - Запускаем апдейт в независимости от состяния прошивки и ее версии.
     * -- Если Update Result - не errors (менее  50) и ver не пустой  - Это означает что пред. апдейт прошел.
     * -- Если Update Result - не errors и ver  пустой  - Это означает что апдейта еще не было или пред. апдейт UnInstall
     * -- Если Update Result - не errors и ver  не пустой  - Это означает что  пред. апдейт UnInstall
     * - Проверяем поменялась ли версия и запускаем новый апдейт.
     */
    private boolean conditionalSwUpdateStart() {
        Long updateResultSw = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        // #1/#2
        return updateResultSw >= LwM2mTransportUtil.UpdateResultSw.NOT_ENOUGH_STORAGE.code ||
                (
                        (updateResultSw <= LwM2mTransportUtil.UpdateResultSw.NOT_ENOUGH_STORAGE.code
                        ) &&
                                (
                                        (this.currentVersion != null && !this.currentVersion.equals(this.lwM2MClient.getResourceValue(null, this.pathVerId))) ||
                                                (this.currentTitle != null && !this.currentTitle.equals(this.lwM2MClient.getResourceValue(null, this.pathNameId)))
                                )
                );
    }

    /**
     * Before operation Execute inspection Update Result :
     * 3 - Successfully Downloaded and package integrity verified
     */
    private boolean conditionalSwUpdateExecute() {
        Long updateResult = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        return LwM2mTransportUtil.UpdateResultSw.SUCCESSFULLY_INSTALLED_VERIFIED.code == updateResult;
    }

    /**
     * After finish operation Execute (success):
     * -- inspection Update Result:
     * ---- FW если Update Result == 1 ("Firmware updated successfully") или  SW если Update Result == 2 ("Software successfully installed.")
     * -- fw_state/sw_state = UPDATED
     *
     * After finish operation Execute (error):
     * -- inspection updateResult and send to thingsboard info about error
     * --- send to telemetry ( key - this is name Update Result in model) (
     * --  fw_state/sw_state = FAILED
     */
    public void finishFwSwUpdateExecute(boolean success) {
        Long updateResult = (Long) this.lwM2MClient.getResourceValue(null, this.pathResultId);
        int updateResultSuccessful = this.type.equals(FIRMWARE) ? LwM2mTransportUtil.UpdateResultFw.UPDATE_SUCCESSFULLY.code :
                LwM2mTransportUtil.UpdateResultSw.SUCCESSFULLY_INSTALLED.code;
        if (success && updateResultSuccessful == updateResult.intValue()) {
            this.stateUpdate = FirmwareUpdateStatus.UPDATED.name();
        }
        else {
            this.stateUpdate = FirmwareUpdateStatus.FAILED.name();
            String value = LwM2mTransportUtil.UpdateResultFw.fromUpdateResultFwByCode(updateResult.intValue()).type;
            String key = splitCamelCaseString((String) this.lwM2MClient.getResourceName (null, this.pathResultId));
            this.serviceImpl.helper.sendParametersOnThingsboardTelemetry(
                    this.serviceImpl.helper.getKvStringtoThingsboard(key, value), this.lwM2MClient.getSession());
        }
        this.sendLogs(EXECUTE.name());
    }

    private void observeStateUpdate() {
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(),
                convertPathFromObjectIdToIdVer(this.pathStateId, this.lwM2MClient.getRegistration()), OBSERVE,
                null, null, 0, null);
        this.serviceImpl.lwM2mTransportRequest.sendAllRequest(lwM2MClient.getRegistration(),
                convertPathFromObjectIdToIdVer(this.pathResultId, this.lwM2MClient.getRegistration()), OBSERVE,
                null, null, 0, null);
    }

    public void sendSateOnThingsboard() {
        if (StringUtils.trimToNull(this.stateUpdate) != null) {
            List<TransportProtos.KeyValueProto> result = new ArrayList<>();
            TransportProtos.KeyValueProto.Builder kvProto = TransportProtos.KeyValueProto.newBuilder().setKey(getAttributeKey(this.type, STATE));
            kvProto.setType(TransportProtos.KeyValueType.STRING_V).setStringV(stateUpdate);
            result.add(kvProto.build());
            this.serviceImpl.helper.sendParametersOnThingsboardTelemetry(result,
                    this.serviceImpl.getSessionInfoOrCloseSession(this.lwM2MClient.getRegistration()));
        }
    }

    public void sendReadInfo(DefaultLwM2MTransportMsgHandler serviceImpl) {
        this.infoFwSwUpdate = true;
        this.serviceImpl = this.serviceImpl == null ? serviceImpl : this.serviceImpl;
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathVerId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathNameId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathStateId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.add(convertPathFromObjectIdToIdVer(
                this.pathResultId, this.lwM2MClient.getRegistration()));
        this.pendingInfoRequestsStart.forEach(pathIdVer -> {
            this.serviceImpl.lwM2mTransportRequest.sendAllRequest(this.lwM2MClient.getRegistration(), pathIdVer, READ, ContentFormat.TLV.getName(),
                    null, 0, null);
        });

    }
}