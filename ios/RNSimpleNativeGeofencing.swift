//
//  IOSGeofenceManager.swift
//  RNSimpleNativeGeofencing
//
//  Created by Fabian Puch on 13.01.19.
//  Copyright © 2019 Facebook. All rights reserved.
//

import Foundation
import CoreLocation
import UserNotifications


@available(iOS 10.0, *)
@objc(RNSimpleNativeGeofencing)
class RNSimpleNativeGeofencing: RCTEventEmitter, CLLocationManagerDelegate, UNUserNotificationCenterDelegate {
    //MARK: - Init / Setup / Vars
    
    static let sharedInstance = RNSimpleNativeGeofencing()
    
    let locationManager = CLLocationManager()
    var notificationCenter: UNUserNotificationCenter?
    
    var didEnterTitle = ""
    var didEnterBody = ""
    var didExitTitle = ""
    var didExitBody = ""
    var startTrackingTitle = ""
    var startTrackingBody = ""
    var stopTrackingTitle = ""
    var stopTrackingBody = ""
    
    var notifyEnter = true
    var notifyExit = false
    var notifyStart = false
    var notifyStop = false
    
    var valueDic: Dictionary<String, NSDictionary> = [:]
    var locationAuthorized = true
    var notificationAuthorized = true
    
    override func supportedEvents() -> [String]! {
        return ["monitorGeofence"]
    }
    
    override init() {
        
    }
    
    fileprivate func allwaysInit() {
        self.locationManager.delegate = self
//        self.locationManager.requestAlwaysAuthorization()
        
        self.notificationCenter = UNUserNotificationCenter.current()
        notificationCenter?.delegate = self
        
//        let options: UNAuthorizationOptions = [.alert, .sound]
//        notificationCenter?.requestAuthorization(options: options) { (granted, error) in
//            if granted {
//                self.notificationAuthorized = true
//            }
//        }
        
    }
    
    
    
    
    
    
    //MARK: -  Public Interface
    
    @objc(initNotification:)
    func initNotification(settings:NSDictionary) -> Void {
        
        DispatchQueue.main.async {
            
            self.allwaysInit()
            
            self.notifyEnter = settings.value(forKeyPath: "enter.notify") as? Bool ?? false
            self.notifyExit = settings.value(forKeyPath: "exit.notify") as? Bool ?? false
            self.notifyStart = settings.value(forKeyPath: "start.notify") as? Bool ?? false
            self.notifyStop = settings.value(forKeyPath: "stop.notify") as? Bool ?? false
            
            self.didEnterTitle = settings.value(forKeyPath: "enter.title") as? String ?? "Be careful!"
            self.didEnterBody = settings.value(forKeyPath: "enter.description") as? String ?? "It may be dangerous in the area where you are currently staying."
            self.didExitTitle = settings.value(forKeyPath: "exit.title") as? String ?? ""
            self.didExitBody = settings.value(forKeyPath: "exit.description") as? String ?? ""
            self.startTrackingTitle = settings.value(forKeyPath: "start.title") as? String ?? ""
            self.startTrackingBody = settings.value(forKeyPath: "start.description") as? String ??  ""
            self.stopTrackingTitle = settings.value(forKeyPath: "stop.title") as? String ?? ""
            self.stopTrackingBody = settings.value(forKeyPath: "stop.description") as? String ??  ""
            
        }
        
    }
    
    @objc(addGeofence:duration:)
    func addGeofence(geofence:NSDictionary, duration:Int) -> Void {
        guard let lat = geofence.value(forKey: "latitude") as? Double else {
            return
        }
        
        guard let lon = geofence.value(forKey: "longitude") as? Double else {
            return
        }
        
        guard let radius = geofence.value(forKey: "radius") as? Double else {
            return
        }
        
        guard let id = geofence.value(forKey: "key") as? String else {
            return
        }
        
        let geofenceRegionCenter = CLLocationCoordinate2D(
            latitude: lat,
            longitude: lon
        )
        
        let geofenceRegion = CLCircularRegion(
            center: geofenceRegionCenter,
            radius: CLLocationDistance(radius),
            identifier: id
        )
        
        self.valueDic[id] = geofence
        
        geofenceRegion.notifyOnExit = true
        geofenceRegion.notifyOnEntry = true
        
        
        if !(duration <= 0) {
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(duration)) {
                self.removeGeofence(geofenceKey: id)
            }
        }
        
        self.startMonitoring(geo: geofenceRegion);
    }
    
    
    @objc(addGeofences:duration:resolver:rejecter:)
    func addGeofences(geofencesArray:NSArray,
                      duration:Int,
                      resolver resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
        //add small geofences
        for geofence in geofencesArray {
            guard let geo = geofence as? NSDictionary else {
                return
            }
            
            self.addGeofence(geofence: geo, duration: 0)
        }
        resolve(true);
        self.notificationCenter?.getNotificationSettings(completionHandler: { (settings) in
            if settings.authorizationStatus == .denied {
                print("Permission not granted")
                self.notificationAuthorized = false
            } else {
                self.notificationAuthorized = true
            }
            
            if !(self.locationAuthorized && self.notificationAuthorized) {
                //                    reject("PERMISSION_DENIED", "Do not have permissions", nil)
            } else {
                
            }
        })
    }
    
    @objc
    func updateGeofences(geofencesArray: NSArray,
                         duration:Int,
                         resolver resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
        
        DispatchQueue.main.async {
            
            
            self.removeAllGeofences(resolver: resolve, rejecter: reject)
            
            //add small geofences
            for geofence in geofencesArray {
                
                guard let geo = geofence as? NSDictionary else {
                    return
                }
                
                self.addGeofence(geofence: geo, duration: duration)
                
            }
            
            self.startSilenceMonitoring()
            
        }
        
    }
    
    
    @objc(addMonitoringBorder:duration:)
    func addMonitoringBorder(geofence:NSDictionary, duration:Int) -> Void {
        
        DispatchQueue.main.async {
            
            
            //monitoring boarder (needs specific ID)
            self.addGeofence(geofence: geofence, duration: duration)
        }
        
    }
    
    
    @objc(removeMonitoringBorder)
    func removeMonitoringBorder() -> Void {
        
        DispatchQueue.main.async {
            for geo in self.locationManager.monitoredRegions {
                self.locationManager.stopMonitoring(for: geo);
            }
        }
    }
    
    
    @objc(removeAllGeofences:rejecter:)
    func removeAllGeofences(resolver resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
        for geo in self.locationManager.monitoredRegions {
            self.valueDic[geo.identifier] = nil
            self.locationManager.stopMonitoring(for: geo)
        }
        
        if self.notifyStop {
            self.notifyStart(started: false)
        }
        resolve(true)
    }
    
    
    @objc(removeGeofence:)
    func removeGeofence(geofenceKey:String) -> Void {
        for geo in self.locationManager.monitoredRegions {
            if geo.identifier == geofenceKey {
                self.valueDic[geo.identifier] = nil
                self.locationManager.stopMonitoring(for: geo)
            }
        }
    }
    
    
    @objc(startMonitoring:)
    func startMonitoring(geo: CLCircularRegion) -> Void {
        // Make sure the app is authorized.
        let status = CLLocationManager.authorizationStatus();
        if status == .authorizedAlways || status == .authorizedWhenInUse {
            // Make sure region monitoring is supported.
            if CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) {
                self.locationManager.startMonitoring(for: geo)
                
                if self.notifyStart {
                    self.notifyStart(started: true)
                }
            }
        }
    }
    
    
    @objc(stopMonitoring)
    func stopMonitoring() -> Void {
        
        DispatchQueue.main.async {
            for geo in self.locationManager.monitoredRegions {
                self.locationManager.stopMonitoring(for: geo)
            }
            
            if self.notifyStop {
                self.notifyStart(started: false)
            }
        }
    }
    
    func stopMonitoring(id: String) {
        for region in locationManager.monitoredRegions {
            guard let circularRegion = region as? CLCircularRegion,
                circularRegion.identifier == id else { continue }
            locationManager.stopMonitoring(for: circularRegion)
        }
    }
    
    
    
    
    //MARK: - helpe
    
    func startSilenceMonitoring() -> Void {
        DispatchQueue.main.async {
            for geo in self.locationManager.monitoredRegions {
                self.locationManager.startMonitoring(for: geo)
            }
        }
    }
    
    //MARK: - Setup Notifications
    
    private func handleEvent(region: CLRegion!, didEnter: Bool) {
        if didEnter {
            let body : [String: Any] = [
                "id": region!.identifier as String,
                "event": "didEnter"
            ]
            
            self.sendEvent(withName: "monitorGeofence", body: body )
        } else {
            let body : [String: Any] = [
                "id": region.identifier as String,
                "event": "didExit"
            ]
            self.sendEvent(withName: "monitorGeofence", body: body )
        }
    
        if (didEnter && !self.notifyEnter) || (!didEnter && !self.notifyExit) {
            return
        }
        let content = UNMutableNotificationContent()
        content.sound = UNNotificationSound.default
        
        
        if self.didEnterBody.contains("[value]") {
            if let geofence = self.valueDic[region.identifier] {
                let value = geofence["value"] as? String ?? "SoThuTu";
                self.didEnterBody = self.didEnterBody.replacingOccurrences(of: "[value]", with: value, options: NSString.CompareOptions.literal, range:nil)
            }
        }
        
        if self.didExitBody.contains("[value]") {
            if let geofence = self.valueDic[region.identifier] {
                let value = geofence["value"] as? String ?? "SoThuTu";
                self.didExitBody = self.didExitBody.replacingOccurrences(of: "[value]", with: value, options: NSString.CompareOptions.literal, range:nil)
            }
        }
        
        var identifier = ""
        
        if didEnter {
            content.title = self.didEnterTitle
            content.body = self.didEnterBody
            identifier = "enter: \(region.identifier)"
        }else{
            content.title = self.didExitTitle
            content.body = self.didExitBody
            identifier = "exit: \(region.identifier)"
        }
        
        
        let timeInSeconds: TimeInterval = 0.1
        
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: timeInSeconds,
            repeats: false
        )
        
        
        let request = UNNotificationRequest(
            identifier: identifier,
            content: content,
            trigger: trigger
        )
        
        notificationCenter?.add(request, withCompletionHandler: { (error) in
            if error != nil {
                print("Error adding notification with identifier: \(identifier)")
            }
        })
        
        
        if !didEnter {
            DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(10)) {
                self.notificationCenter?.removeDeliveredNotifications(withIdentifiers: ["enter: \(region.identifier)","exit: \(region.identifier)"])
                
            }
        }
    }
    
    
    private func notifyStart(started: Bool) {
        
        let content = UNMutableNotificationContent()
        content.sound = UNNotificationSound.default
        
        
        if started {
            content.title = self.startTrackingTitle
            content.body = self.startTrackingBody
        }else{
            content.title = self.stopTrackingTitle
            content.body = self.startTrackingBody
        }
        
        
        let timeInSeconds: TimeInterval = 0.1
        
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: timeInSeconds,
            repeats: false
        )
        
        let identifier = self.randomString(length: 20)
        
        let request = UNNotificationRequest(
            identifier: identifier,
            content: content,
            trigger: trigger
        )
        
        notificationCenter?.add(request, withCompletionHandler: { (error) in
            if error != nil {
                print("Error adding notification with identifier: \(identifier)")
            }
        })
    }
    
    
    
    
    //MARK: - Location Delegate Methodes
    
    func locationManager(_ manager: CLLocationManager, didStartMonitoringFor region: CLRegion) {
        print("BM didStartMonitoringForRegion")
        locationManager.requestState(for: region) // should locationManager be manager?
    }
    
    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        if region is CLCircularRegion {
            self.handleEvent(region:region, didEnter: true)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        if region is CLCircularRegion {
            self.handleEvent(region:region, didEnter: false)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .denied {
            print("Geofence will not Work, because of missing Authorization")
            locationAuthorized = false
        }else{
            locationAuthorized = true
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        print("BM didDetermineState \(state)");
        switch state {
        case .inside:
            print("BeaconManager:didDetermineState CLRegionState.Inside \(region.identifier)");
            self.handleEvent(region: region, didEnter: true)
        case .outside:
            print("BeaconManager:didDetermineState CLRegionState.Outside");
        case .unknown:
            print("BeaconManager:didDetermineState CLRegionState.Unknown");
        default:
            print("BeaconManager:didDetermineState default");
        }
    }
    
    
    
    //MARK: - Notification Delegate Methodes
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // when app is onpen and in foregroud
        completionHandler(.alert)
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        
        // get the notification identifier to respond accordingly
        let identifier = response.notification.request.identifier
        
        // do what you need to do
        print(identifier)
        // ...
    }
    
    
    
    
    
    //MARK: - helper Functions
    
    func randomString(length: Int) -> String {
        let letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return String((0...length-1).map{ _ in letters.randomElement()! })
    }
    
    
}
