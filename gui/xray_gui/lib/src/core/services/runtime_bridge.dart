import '../models/profile.dart';

abstract class RuntimeBridge {
  Future<void> requestVpnPermission();

  Future<void> start(Profile profile, Map<String, dynamic> config);

  Future<void> stop();

  Future<String> runtimeState();

  Future<void> updateGeoData();

  Stream<String> logs();
}
