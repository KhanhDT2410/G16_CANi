// CANi · tab "Tài khoản" — Block 4 · CV & Nghề nghiệp.
// Chưa có API CV (UserDto không có field CV) → 4 dòng placeholder
// "Chưa có dữ liệu" + nhãn "Sắp ra mắt". Không bịa field CV.

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileCvBlock extends StatelessWidget {
  const ProfileCvBlock({super.key});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    const rows = ['Kinh nghiệm', 'Lương mong muốn', 'Khu vực', 'Giới thiệu'];
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Text(
            'CV & Nghề nghiệp',
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: t.text,
                  fontWeight: FontWeight.bold,
                ),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
            decoration: BoxDecoration(
                color: t.chipSoft,
                borderRadius: BorderRadius.circular(99),
                border: Border.all(color: t.chipSoft)),
            child: Text(
              'Sắp ra mắt',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: const Color(0xFFB45309),
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
        ]),
        const SizedBox(height: 10),
        for (var i = 0; i < rows.length; i++) ...[
          Row(children: [
            Expanded(
              child: Text(
                rows[i],
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.w500,
                    ),
              ),
            ),
            Text('Chưa có dữ liệu',
                style: Theme.of(context)
                    .textTheme
                    .bodySmall
                    ?.copyWith(color: t.muted, fontStyle: FontStyle.italic)),
          ]),
          if (i < rows.length - 1) const Divider(height: 18, color: Colors.transparent),
        ],
      ]),
    );
  }
}
