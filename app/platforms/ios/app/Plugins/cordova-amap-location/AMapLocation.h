#import <Cordova/CDVPlugin.h>
#import <AMapLocationKit/AMapLocationKit.h>


@interface AMapLocation : CDVPlugin {}

@property (retain, nonatomic) IBOutlet NSString *callback;
@property (retain, nonatomic) IBOutlet NSString *fenceCallback;

@property (nonatomic, strong) AMapLocationManager *locationManager;
@property (nonatomic, strong) AMapGeoFenceManager *geoFenceManager;

- (void) initConfig;

- (void) initFenceConfig;

- (void)getCurrentPosition:(CDVInvokedUrlCommand*)command;

- (void)watchPosition:(CDVInvokedUrlCommand*)command;

- (void)clearWatch:(CDVInvokedUrlCommand*)command;

@end
