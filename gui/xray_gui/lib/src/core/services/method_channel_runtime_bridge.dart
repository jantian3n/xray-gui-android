import 'package:flutter/services.dart';

import '../models/profile.dart';
import 'runtime_bridge.dart';

class MethodChannelRuntimeBridge implements RuntimeBridge {
  static const MethodChannel _methodChannel = MethodChannel('xray_gui/runtime');
  static const EventChannel _logChannel = EventChannel('xray_gui/runtime_logs');

  @override
  Future<void> requestVpnPermission() async {
    await _methodChannel.invokeMethod<void>('requestVpnPermission');
  }

  @override
  Future<void> start(Profile profile, Map<String, dynamic> config) async {
    await _methodChannel.invokeMethod<void>('start', <String, dynamic>{
      'profile': profile.toJson(),
      'config': config,
    });
  }

  @override
  Future<void> stop() async {
    await _methodChannel.invokeMethod<void>('stop');
  }

  @override
  Future<String> runtimeState() async {
    final value = await _methodChannel.invokeMethod<String>('runtimeState');
    return value ?? 'unknown';
  }

  @override
  Future<void> updateGeoData() async {
    await _methodChannel.invokeMethod<void>('updateGeoData');
  }

  @override
  Stream<String> logs() {
    return _logChannel.receiveBroadcastStream().map((dynamic event) => '$event');
  }
}
