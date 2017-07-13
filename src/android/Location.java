package com.phonegap.plugins.aMapLocation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.amap.api.fence.GeoFence;
import com.amap.api.fence.GeoFenceClient;
import com.amap.api.fence.GeoFenceListener;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationClientOption.AMapLocationMode;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.DPoint;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;

public class Location extends CordovaPlugin implements AMapLocationListener {

    String TAG = "GeolocationPlugin";
    String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    public static AMapLocationClient keepLocationInstance = null;
    private AMapLocationClient locationClient = null;
    private AMapLocationClientOption locationOption = null;
    boolean keepSendBack = false;
    CallbackContext callback;
    //实例化地理围栏客户端
    GeoFenceClient mGeoFenceClient = null;

    public static String sHA1(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] publicKey = md.digest(cert);
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < publicKey.length; i++) {
                String appendString = Integer.toHexString(0xFF & publicKey[i])
                        .toUpperCase(Locale.US);
                if (appendString.length() == 1)
                    hexString.append("0");
                hexString.append(appendString);
                hexString.append(":");
            }
            String result = hexString.toString();
            return result.substring(0, result.length() - 1);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean execute(String action, final JSONArray args, CallbackContext callbackContext) throws JSONException {
        callback = callbackContext;

        if (locationClient == null) {
            locationClient = new AMapLocationClient(this.cordova.getActivity().getApplicationContext());
//            locationClient.setApiKey("e41317b3e3c5b9227a0795d3e1abd472");
        }

        if (action.equals("getCurrentPosition")) {
            locationOption = new AMapLocationClientOption();

            // 设置定位模式为高精度模式
            locationOption.setLocationMode(AMapLocationMode.Hight_Accuracy);
            //设置为单次定位
            locationOption.setOnceLocation(true);
            // 设置定位监听
            locationClient.setLocationListener(this);
            locationOption.setNeedAddress(true);
            locationOption.setInterval(2000);

            locationClient.setLocationOption(locationOption);
            // 启动定位
            locationClient.startLocation();
            return true;
        } else if (action.equals("watchPosition")) { //启动持续定位
            if (keepLocationInstance != null) { //判断是否存在未关闭的持续定位对象
                keepLocationInstance.stopLocation();
                keepLocationInstance.onDestroy();
                keepLocationInstance = null;
            }

            int interval = args.optInt(0, 10000); //获取定位间隔参数，缺省10秒钟定位一次

            locationOption = new AMapLocationClientOption();

            // 设置定位模式为高精度模式
            locationOption.setLocationMode(AMapLocationMode.Hight_Accuracy);
            //设置为多次定位
            locationOption.setOnceLocation(false);
            // 设置定位监听
            locationClient.setLocationListener(this);
            locationOption.setNeedAddress(true);
            locationOption.setInterval(interval);
            locationClient.setLocationOption(locationOption);
            // 启动定位
            locationClient.startLocation();
            keepSendBack = true;
            // 存储持续定位对象用于关闭
            keepLocationInstance = locationClient;
            return true;
        } else if (action.equals("clearWatch")) { //停止持续定位
            if (keepLocationInstance != null) {
                keepLocationInstance.stopLocation();
                keepLocationInstance.onDestroy();
                keepLocationInstance = null;
            }
            callback.success();
            return true;
        } else if (action.equals("addCircleRegionForMonitoringWithCenter")) {


            this.cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    addCircleRegionForMonitoringWithCenter(args);
                }
            });

            return true;
        } else if (action.equals("removeAllFence")) {
            //实例化地理围栏客户端
            if (mGeoFenceClient == null) {
                mGeoFenceClient = new GeoFenceClient(this.cordova.getActivity().getApplicationContext());
            }

            //会清除所有围栏
            mGeoFenceClient.removeGeoFence();
            return true;
        } else if(action.equals("removeCustomFence")){
            List<GeoFence> geoFences = mGeoFenceClient.getAllGeoFence();
            String customIdToRemove = args.getString(0);

            for(GeoFence fence: geoFences) {
                String customId = fence.getCustomId();

                if(customIdToRemove == customId) {
                    mGeoFenceClient.removeGeoFence(fence);

                    break;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private void addCircleRegionForMonitoringWithCenter(JSONArray args) {
        //实例化地理围栏客户端
        if (mGeoFenceClient == null) {
            String sha1 = sHA1(this.cordova.getActivity().getApplicationContext());
            mGeoFenceClient = new GeoFenceClient(this.cordova.getActivity().getApplicationContext());
            Log.d("sha1", "sha1: " + sha1);
        }


        long latitude = 0;
        long longitude = 0;
        long radius = 0;
        String customId = "";

        //设置希望侦测的围栏触发行为，默认只侦测用户进入围栏的行为
        //public static final int GEOFENCE_IN 进入地理围栏
        //public static final int GEOFENCE_OUT 退出地理围栏
        //public static final int GEOFENCE_STAYED 停留在地理围栏内10分钟
        try {

            latitude = args.getLong(0);
            longitude = args.getLong(1);
            radius = args.getLong(2);
            customId = args.getString(3);

        } catch (JSONException e) {
            Log.e("[JSON ERROR]", e.toString());
        }

        mGeoFenceClient.setActivateAction(mGeoFenceClient.GEOFENCE_IN | mGeoFenceClient.GEOFENCE_OUT | mGeoFenceClient.GEOFENCE_STAYED);

            /*
                mGeoFenceClient.addGeoFence(Point point,float radius, String customId);

                point
                围栏中心点

                radius
                要创建的围栏半径 ，半径无限制，单位米

                customId
                与围栏关联的自有业务Id
            */

        //创建一个中心点坐标
        DPoint centerPoint = new DPoint();
        //设置中心点纬度
        centerPoint.setLatitude(latitude);
        //设置中心点经度
        centerPoint.setLongitude(longitude);

        //执行添加围栏的操作
        mGeoFenceClient.addGeoFence(centerPoint, radius, customId);

        //创建回调监听
        GeoFenceListener fenceListenter = new GeoFenceListener() {

            @Override
            public void onGeoFenceCreateFinished(
                    List<GeoFence> geoFenceList,
                    int errorCode,
                    String customId
            ) {

                Log.d("[FENCE]", "error code：" + errorCode + "， customID：" + customId);

                if (errorCode == GeoFence.ADDGEOFENCE_SUCCESS) {//判断围栏是否创建成功
                    //tvReult.setText("添加围栏成功!!");
                    //geoFenceList就是已经添加的围栏列表，可据此查看创建的围栏
                    for (GeoFence item : geoFenceList) {
                        Log.d("[FENCE]", item.getCenter().toString());
                    }

                    PluginResult result = new PluginResult(PluginResult.Status.OK, "地理围栏创建成功！");
                    callback.sendPluginResult(result);
                } else {
                    //geoFenceList就是已经添加的围栏列表
                    //tvReult.setText("添加围栏失败!!");
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, "地理围栏创建失败：！" + errorCode);
                    callback.sendPluginResult(result);
                }
            }
        };
        //设置回调监听
        mGeoFenceClient.setGeoFenceListener(fenceListenter);


        //定义接收广播的action字符串
        final String GEOFENCE_BROADCAST_ACTION = "com.cloudtea.geofence.broadcast";
        //创建并设置PendingIntent
        mGeoFenceClient.createPendingIntent(GEOFENCE_BROADCAST_ACTION);

        BroadcastReceiver mGeoFenceReceiver = new BroadcastReceiver() {


            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(GEOFENCE_BROADCAST_ACTION)) {
                    fenceReceived(context, intent);
                }
            }
        };


        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(GEOFENCE_BROADCAST_ACTION);
        this.cordova.getActivity().registerReceiver(mGeoFenceReceiver, filter);
    }

    private void fenceReceived(Context context, Intent intent) {
        if (mGeoFenceClient != null) {

            JSONObject fenceInfo = new JSONObject();
            //解析广播内容
            //获取Bundle
            Bundle bundle = intent.getExtras();
            //获取围栏行为：
            int status = bundle.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS);
            //获取自定义的围栏标识：
            String customId = bundle.getString(GeoFence.BUNDLE_KEY_CUSTOMID);
            //获取围栏ID:
            String fenceId = bundle.getString(GeoFence.BUNDLE_KEY_FENCEID);
            //获取当前有触发的围栏对象：
            GeoFence fence = bundle.getParcelable(GeoFence.BUNDLE_KEY_FENCE);

            // todo: 发送处理结果给插件
            try {

                fenceInfo.put("status", status);
                fenceInfo.put("customeId", customId);
                fenceInfo.put("fenceId", fenceId);
                fenceInfo.put("fence", fence);

            } catch (JSONException e) {
                Log.e(TAG, "fence json error:" + e);
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, fenceInfo);
            callback.sendPluginResult(result);
        }
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = new Date(aMapLocation.getTime());
                df.format(date);//定位时间

                JSONObject locationInfo = new JSONObject();
                try {
                    locationInfo.put("locationType", aMapLocation.getLocationType()); //获取当前定位结果来源，如网络定位结果，详见定位类型表
                    locationInfo.put("latitude", aMapLocation.getLatitude()); //获取纬度
                    locationInfo.put("longitude", aMapLocation.getLongitude()); //获取经度
                    locationInfo.put("accuracy", aMapLocation.getAccuracy()); //获取精度信息
                    locationInfo.put("speed", aMapLocation.getSpeed()); //获取速度信息
                    locationInfo.put("bearing", aMapLocation.getBearing()); //获取方向信息
                    locationInfo.put("date", date); //定位时间
                    locationInfo.put("address", aMapLocation.getAddress()); //地址，如果option中设置isNeedAddress为false，则没有此结果
                    locationInfo.put("country", aMapLocation.getCountry()); //国家信息
                    locationInfo.put("province", aMapLocation.getProvince()); //省信息
                    locationInfo.put("city", aMapLocation.getCity()); //城市信息
                    locationInfo.put("district", aMapLocation.getDistrict()); //城区信息
                    locationInfo.put("street", aMapLocation.getStreet()); //街道信息
                    locationInfo.put("streetNum", aMapLocation.getStreetNum()); //街道门牌号
                    locationInfo.put("cityCode", aMapLocation.getCityCode()); //城市编码
                    locationInfo.put("adCode", aMapLocation.getAdCode()); //地区编码
                    locationInfo.put("poiName", aMapLocation.getPoiName());
                    locationInfo.put("aoiName", aMapLocation.getAoiName());
                } catch (JSONException e) {
                    Log.e(TAG, "Locatioin json error:" + e);
                }
                PluginResult result = new PluginResult(PluginResult.Status.OK, locationInfo);
                if (!keepSendBack) { //不持续传回定位信息
                    locationClient.stopLocation(); //只获取一次的停止定位
                } else {
                    result.setKeepCallback(true);
                }
                callback.sendPluginResult(result);
            } else {
                //显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                Log.e(TAG, "Locatioin error:" + aMapLocation.getErrorCode());
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, aMapLocation.getErrorCode());
                callback.sendPluginResult(result);
            }
        }
    }


    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        PluginResult result;
        //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
        if (callback != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    LOG.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                    callback.sendPluginResult(result);
                    return;
                }

            }
            result = new PluginResult(PluginResult.Status.OK);
            callback.sendPluginResult(result);
        }
    }
}
