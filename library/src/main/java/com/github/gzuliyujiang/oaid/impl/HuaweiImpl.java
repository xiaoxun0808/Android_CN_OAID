/*
 * Copyright (c) 2019-2021 gzu-liyujiang <1032694760@qq.com>
 *
 * The software is licensed under the Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *     http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR
 * PURPOSE.
 * See the Mulan PSL v2 for more details.
 *
 */
package com.github.gzuliyujiang.oaid.impl;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.gzuliyujiang.oaid.IGetter;
import com.github.gzuliyujiang.oaid.IOAID;
import com.github.gzuliyujiang.oaid.OAIDLog;

import repeackage.com.uodis.opendevice.aidl.OpenDeviceIdentifierService;

/**
 * 参阅华为官方 HUAWEI Ads SDK。
 * <prev>
 * implementation `com.huawei.hms:ads-identifier:3.4.39.302`
 * AdvertisingIdClient.getAdvertisingIdInfo(context).getId()
 * </pre> *
 *
 * @author 大定府羡民（1032694760@qq.com）
 * @since 2020/5/30
 */
class HuaweiImpl implements IOAID {
    private final Context context;
    private String packageName;

    public HuaweiImpl(Context context) {
        this.context = context;
    }

    @Override
    public boolean supported() {
        try {
            PackageManager pm = context.getPackageManager();
            packageName = "com.huawei.hwid";
            if (pm.getPackageInfo(packageName, 0) != null) {
                return true;
            }
            packageName = "com.huawei.hwid.tv";
            if (pm.getPackageInfo(packageName, 0) != null) {
                return true;
            }
            packageName = "com.huawei.hms";
            return pm.getPackageInfo(packageName, 0) != null;
        } catch (Throwable e) {
            OAIDLog.print(e);
            return false;
        }
    }

    @Override
    public void doGet(@NonNull final IGetter getter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                String oaid = Settings.Global.getString(context.getContentResolver(), "pps_oaid");
                if (!TextUtils.isEmpty(oaid)) {
                    getter.onOAIDGetComplete(oaid);
                    return;
                }
            } catch (Throwable e) {
                OAIDLog.print(e);
            }
        }
        if (TextUtils.isEmpty(packageName) && !supported()) {
            getter.onOAIDGetError(new RuntimeException("Huawei Advertising ID not available"));
            return;
        }
        Intent intent = new Intent("com.uodis.opendevice.OPENIDS_SERVICE");
        intent.setPackage(packageName);
        try {
            boolean isBinded = context.bindService(intent, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    OAIDLog.print("Huawei OPENIDS_SERVICE connected");
                    try {
                        OpenDeviceIdentifierService anInterface = OpenDeviceIdentifierService.Stub.asInterface(service);
                        if (anInterface.isOaidTrackLimited()) {
                            // 实测在系统设置中关闭了广告标识符，将获取到固定的一大堆0
                            getter.onOAIDGetError(new RuntimeException("User has disabled advertising identifier"));
                            return;
                        }
                        String oaid = anInterface.getOaid();
                        if (oaid == null || oaid.length() == 0) {
                            throw new RuntimeException("Huawei Advertising ID get failed");
                        }
                        getter.onOAIDGetComplete(oaid);
                    } catch (Throwable e) {
                        OAIDLog.print(e);
                        getter.onOAIDGetError(e);
                    } finally {
                        try {
                            context.unbindService(this);
                        } catch (Throwable e) {
                            OAIDLog.print(e);
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    OAIDLog.print("Huawei OPENIDS_SERVICE disconnected");
                }
            }, Context.BIND_AUTO_CREATE);
            if (!isBinded) {
                throw new RuntimeException("Huawei OPENIDS_SERVICE bind failed");
            }
        } catch (Throwable e) {
            getter.onOAIDGetError(e);
        }
    }

}