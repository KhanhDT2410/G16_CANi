import 'package:flutter/material.dart';

/// OtpPinInputRow - Cụm 6 ô nhập mã OTP tự động chuyển focus
class OtpPinInputRow extends StatelessWidget {
  final List<TextEditingController> controllers;
  final List<FocusNode> focusNodes;
  final double screenWidth;

  const OtpPinInputRow({
    super.key,
    required this.controllers,
    required this.focusNodes,
    required this.screenWidth,
  }) : assert(controllers.length == 6 && focusNodes.length == 6);

  @override
  Widget build(BuildContext context) {
    final boxWidth = (screenWidth > 380) ? 46.0 : 42.0;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: List.generate(6, (index) {
        return SizedBox(
          width: boxWidth,
          height: 54,
          child: TextFormField(
            controller: controllers[index],
            focusNode: focusNodes[index],
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            maxLength: 1,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: Color(0xFF0B1B3D),
            ),
            decoration: InputDecoration(
              counterText: '',
              contentPadding: EdgeInsets.zero,
              filled: true,
              fillColor: const Color(0xFFF8FAFC),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(
                  color: Color(0xFFE2E8F0),
                  width: 1.5,
                ),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(
                  color: Color(0xFFE2E8F0),
                  width: 1.5,
                ),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(
                  color: Color(0xFF0077FF),
                  width: 2,
                ),
              ),
            ),
            onChanged: (val) {
              if (val.isNotEmpty && index < 5) {
                focusNodes[index + 1].requestFocus();
              } else if (val.isEmpty && index > 0) {
                focusNodes[index - 1].requestFocus();
              }
            },
          ),
        );
      }),
    );
  }
}

