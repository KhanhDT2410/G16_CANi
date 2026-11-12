class BankInfo {
  final String code;
  final String name;
  final String shortName;

  const BankInfo({
    required this.code,
    required this.name,
    required this.shortName,
  });
}

const List<BankInfo> kSupportedBanks = [
  BankInfo(code: 'MB', name: 'Ngân hàng Quân Đội (MBBank)', shortName: 'MBBank'),
  BankInfo(code: 'VCB', name: 'Ngân hàng Ngoại Thương (Vietcombank)', shortName: 'Vietcombank'),
  BankInfo(code: 'TCB', name: 'Ngân hàng Kỹ Thương (Techcombank)', shortName: 'Techcombank'),
  BankInfo(code: 'BIDV', name: 'Ngân hàng Đầu tư & Phát triển (BIDV)', shortName: 'BIDV'),
  BankInfo(code: 'CTG', name: 'Ngân hàng Công Thương (VietinBank)', shortName: 'VietinBank'),
  BankInfo(code: 'ACB', name: 'Ngân hàng Á Châu (ACB)', shortName: 'ACB'),
  BankInfo(code: 'TPB', name: 'Ngân hàng Tiên Phong (TPBank)', shortName: 'TPBank'),
  BankInfo(code: 'VPB', name: 'Ngân hàng Việt Nam Thịnh Vượng (VPBank)', shortName: 'VPBank'),
  BankInfo(code: 'VBA', name: 'Ngân hàng Nông nghiệp (Agribank)', shortName: 'Agribank'),
  BankInfo(code: 'OCB', name: 'Ngân hàng Phương Đông (OCB)', shortName: 'OCB'),
];
