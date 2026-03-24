class VlessNode {
  const VlessNode({
    required this.name,
    required this.address,
    required this.port,
    required this.id,
    required this.network,
    required this.security,
    this.encryption = 'none',
    this.flow = '',
    this.serverName = '',
    this.fingerprint = '',
    this.publicKey = '',
    this.shortId = '',
    this.spiderX = '',
    this.host = '',
    this.path = '',
    this.mode = '',
    this.extras = const <String, String>{},
  });

  final String name;
  final String address;
  final int port;
  final String id;
  final String network;
  final String security;
  final String encryption;
  final String flow;
  final String serverName;
  final String fingerprint;
  final String publicKey;
  final String shortId;
  final String spiderX;
  final String host;
  final String path;
  final String mode;
  final Map<String, String> extras;

  bool get isReality => security.toLowerCase() == 'reality';

  bool get isXhttp {
    final value = network.toLowerCase();
    return value == 'xhttp' || value == 'splithttp';
  }

  VlessNode copyWith({
    String? name,
    String? address,
    int? port,
    String? id,
    String? network,
    String? security,
    String? encryption,
    String? flow,
    String? serverName,
    String? fingerprint,
    String? publicKey,
    String? shortId,
    String? spiderX,
    String? host,
    String? path,
    String? mode,
    Map<String, String>? extras,
  }) {
    return VlessNode(
      name: name ?? this.name,
      address: address ?? this.address,
      port: port ?? this.port,
      id: id ?? this.id,
      network: network ?? this.network,
      security: security ?? this.security,
      encryption: encryption ?? this.encryption,
      flow: flow ?? this.flow,
      serverName: serverName ?? this.serverName,
      fingerprint: fingerprint ?? this.fingerprint,
      publicKey: publicKey ?? this.publicKey,
      shortId: shortId ?? this.shortId,
      spiderX: spiderX ?? this.spiderX,
      host: host ?? this.host,
      path: path ?? this.path,
      mode: mode ?? this.mode,
      extras: extras ?? this.extras,
    );
  }

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'name': name,
      'address': address,
      'port': port,
      'id': id,
      'network': network,
      'security': security,
      'encryption': encryption,
      'flow': flow,
      'serverName': serverName,
      'fingerprint': fingerprint,
      'publicKey': publicKey,
      'shortId': shortId,
      'spiderX': spiderX,
      'host': host,
      'path': path,
      'mode': mode,
      'extras': extras,
    };
  }
}
