#import "AMapLocation.h"
#import <AMapFoundationKit/AMapFoundationKit.h>
#import <AMapLocationKit/AMapLocationKit.h>


@implementation AMapLocation

//init Config
-(void) initConfig{
    if(!self.locationManager){
        //set APIKey
        NSDictionary* infoDict = [[NSBundle mainBundle] infoDictionary];
        NSString* appKey = [infoDict objectForKey:@"AMapAppKey"];
        [AMapServices sharedServices].apiKey = appKey;
        
        //init locationManager
        self.locationManager = [[AMapLocationManager alloc]init];
        self.locationManager.delegate = self;
        //set DesiredAccuracy
        [self.locationManager setDesiredAccuracy:kCLLocationAccuracyHundredMeters];
    }
}

-(void) initFenceConfig {
    if(!self.geoFenceManager) {
        self.geoFenceManager = [[AMapGeoFenceManager alloc] init];
        self.geoFenceManager.delegate = self;
        self.geoFenceManager.activeAction = AMapGeoFenceActiveActionInside | AMapGeoFenceActiveActionOutside | AMapGeoFenceActiveActionStayed; //设置希望侦测的围栏触发行为，默认是侦测用户进入围栏的行为，即AMapGeoFenceActiveActionInside，这边设置为进入，离开，停留（在围栏内10分钟以上），都触发回调
        self.geoFenceManager.allowsBackgroundLocationUpdates = YES;  //允许后台定位
    }
}

/*
 - (void)addCircleRegionForMonitoringWithCenter:(CLLocationCoordinate2D)center radius:(CLLocationDistance)radius customID:(NSString *)customID;
 参数         说明          示例
 center     围栏中心点的经纬度
 radius     要创建的围栏的半径，半径大于0，单位米
 customID   与围栏关联的自有业务ID
 */
- (void)addCircleRegionForMonitoringWithCenter:(CDVInvokedUrlCommand*)command {
    [self initFenceConfig];

    float latitude = [[command.arguments objectAtIndex:0] doubleValue];
    float longitude = [[command.arguments objectAtIndex:1] doubleValue];
    float radius = [[command.arguments objectAtIndex:2] doubleValue];
    NSString* customId = [command.arguments objectAtIndex:3];
    
    self.fenceCallback = command.callbackId;
    CLLocationCoordinate2D coordinate = CLLocationCoordinate2DMake(latitude, longitude); //天安门
    
    [self.geoFenceManager addCircleRegionForMonitoringWithCenter:coordinate radius:radius customID:customId];
}

- (void) removeAllFence:(CDVInvokedUrlCommand*)command  {
    [self.geoFenceManager removeAllGeoFenceRegions];
    NSLog(@"所有地理围栏移除");
}

- (void)amapGeoFenceManager:(AMapGeoFenceManager *)manager didAddRegionForMonitoringFinished:(NSArray<AMapGeoFenceRegion *> *)regions customID:(NSString *)customID error:(NSError *)error {
    CDVPluginResult* result = nil;

    if (error) {
        
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
        NSLog(@"创建失败 %@",error);
    } else {
        
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"{status:0, msg:'创建围栏成功！'}"];
        NSLog(@"创建成功");
    }
    
    if (result) {
        [result setKeepCallbackAsBool:YES];
        [[self commandDelegate] sendPluginResult:result callbackId: self.fenceCallback];
    }
}

- (void)amapGeoFenceManager:(AMapGeoFenceManager *)manager didGeoFencesStatusChangedForRegion:(AMapGeoFenceRegion *)region customID:(NSString *)customID error:(NSError *)error {
    CDVPluginResult* result = nil;
    
    if (error) {
        NSLog(@"status changed error %@",error);
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }else{
        NSLog(@"status changed success %@",[region description]);
    
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[region description]];
    }
    
    if (result) {
        [result setKeepCallbackAsBool:YES];
        [[self commandDelegate] sendPluginResult:result callbackId: self.fenceCallback];
    }
}

- (void)getCurrentPosition:(CDVInvokedUrlCommand*)command
{
    [self initConfig];
    
    //   定位超时时间，最低2s，此处设置为5s
    self.locationManager.locationTimeout = 5;
    //   逆地理请求超时时间，最低2s，此处设置为5s
    self.locationManager.reGeocodeTimeout = 5;
    
    __weak AMapLocation *weakSelf = self;
    [self.locationManager requestLocationWithReGeocode:YES completionBlock:^(CLLocation *location, AMapLocationReGeocode *regeocode, NSError *error) {
        
        if (error) {
            NSLog(@"locError:{%ld - %@};", (long)error.code, error.localizedDescription);
            NSDictionary *addressInfo = @{@"code": [NSNumber numberWithInteger:error.code],
                                          @"message": error.localizedDescription};
            
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:addressInfo];
            [weakSelf.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } else {
            NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
            [dateFormatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
            
            NSDictionary *addressInfo = @{@"latitude": [NSNumber numberWithDouble:location.coordinate.latitude],
                                          @"longitude": [NSNumber numberWithDouble:location.coordinate.longitude],
                                          @"speed": [NSNumber numberWithDouble:location.speed],
                                          @"bearing": [NSNumber numberWithDouble:location.course],
                                          @"accuracy": [NSNumber numberWithDouble:location.horizontalAccuracy],
                                          @"date": [dateFormatter stringFromDate:location.timestamp],
                                          @"address": regeocode.formattedAddress ?: @"",
                                          @"country": regeocode.country ?: @"",
                                          @"province": regeocode.province ?: @"",
                                          @"city": regeocode.city ?: @"",
                                          @"cityCode": regeocode.citycode ?: @"",
                                          @"district": regeocode.district ?: @"",
                                          @"street": regeocode.street ?: @"",
                                          @"streetNum": regeocode.number ?: @"",
                                          @"adCode": regeocode.adcode ?: @"",
                                          @"poiName": regeocode.POIName ?: @"",
                                          @"aoiName": regeocode.AOIName ?: @""};
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:addressInfo];
            [weakSelf.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }];
}

- (void)watchPosition:(CDVInvokedUrlCommand*)command
{
    [self initConfig];
    self.callback = command.callbackId;
    [self.locationManager setLocatingWithReGeocode:YES];
    [self.locationManager startUpdatingLocation];
}

- (void)amapLocationManager:(AMapLocationManager *)manager didUpdateLocation:(CLLocation *)location reGeocode:(AMapLocationReGeocode *)reGeocode
{
    NSLog(@"location:{lat:%f; lon:%f; accuracy:%f}", location.coordinate.latitude, location.coordinate.longitude, location.horizontalAccuracy);
    CDVPluginResult* result = nil;
    if (reGeocode) {
        NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
        [dateFormatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
        
        NSDictionary *addressInfo = @{@"latitude": [NSNumber numberWithDouble:location.coordinate.latitude],
                                      @"longitude": [NSNumber numberWithDouble:location.coordinate.longitude],
                                      @"speed": [NSNumber numberWithDouble:location.speed],
                                      @"bearing": [NSNumber numberWithDouble:location.course],
                                      @"accuracy": [NSNumber numberWithDouble:location.horizontalAccuracy],
                                      @"date": [dateFormatter stringFromDate:location.timestamp],
                                      @"address": reGeocode.formattedAddress ?: @"",
                                      @"country": reGeocode.country ?: @"",
                                      @"province": reGeocode.province ?: @"",
                                      @"city": reGeocode.city ?: @"",
                                      @"cityCode": reGeocode.citycode ?: @"",
                                      @"district": reGeocode.district ?: @"",
                                      @"street": reGeocode.street ?: @"",
                                      @"streetNum": reGeocode.number ?: @"",
                                      @"adCode": reGeocode.adcode ?: @"",
                                      @"poiName": reGeocode.POIName ?: @"",
                                      @"aoiName": reGeocode.AOIName ?: @""};
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:addressInfo];
        
    } else {
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    if (result) {
        [result setKeepCallbackAsBool:YES];
        [[self commandDelegate] sendPluginResult:result callbackId: self.callback];
    }
}

- (void)clearWatch:(CDVInvokedUrlCommand*)command
{
    [self initConfig];
    [self.locationManager stopUpdatingLocation];
    CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK];
    [[self commandDelegate] sendPluginResult:result callbackId: command.callbackId];
}

@end
