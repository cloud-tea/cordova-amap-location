cordova.define('cordova/plugin_list', function(require, exports, module) {
module.exports = [
    {
        "id": "cordova-amap-location.AMapLocation",
        "file": "plugins/cordova-amap-location/www/location.js",
        "pluginId": "cordova-amap-location",
        "clobbers": [
            "window.plugins.aMapLocationPlugin"
        ]
    }
];
module.exports.metadata = 
// TOP OF METADATA
{
    "cordova-plugin-whitelist": "1.3.2",
    "cordova-amap-location": "1.0.6"
};
// BOTTOM OF METADATA
});