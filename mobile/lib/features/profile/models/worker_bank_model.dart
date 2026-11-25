class WorkerBankModel {
  final String? bankCode;
  final String? bankAccountNumber;
  final String? bankAccountNumberMasked;
  final String? bankAccountName;
  final String? vietQrUrl;

  const WorkerBankModel({
    this.bankCode,
    this.bankAccountNumber,
    this.bankAccountNumberMasked,
    this.bankAccountName,
    this.vietQrUrl,
  });

  bool get isConfigured =>
      bankAccountNumber != null &&
      bankAccountNumber!.trim().isNotEmpty &&
      bankCode != null &&
      bankCode!.trim().isNotEmpty;

  factory WorkerBankModel.fromJson(Map<String, dynamic> json) {
    return WorkerBankModel(
      bankCode: json['bankCode'] as String?,
      bankAccountNumber: json['bankAccountNumber'] as String?,
      bankAccountNumberMasked: json['bankAccountNumberMasked'] as String?,
      bankAccountName: json['bankAccountName'] as String?,
      vietQrUrl: json['vietQrUrl'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'bankCode': bankCode,
      'bankAccountNumber': bankAccountNumber,
      'bankAccountNumberMasked': bankAccountNumberMasked,
      'bankAccountName': bankAccountName,
      'vietQrUrl': vietQrUrl,
    };
  }
}
