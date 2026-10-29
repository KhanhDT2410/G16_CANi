// CANi · tab "Tài khoản" — Block 7 · Public Profile / Hồ sơ công khai.
// Toggle + link chia sẻ (disabled "coming soon") + gợi ý preview. Chưa có API.

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfilePublicProfileBlock extends StatelessWidget {
  const ProfilePublicProfileBlock({super.key});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
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
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(
                'Hồ sơ công khai',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 4),
              Text('Hiển thị hồ sơ cho người tổ chức sự kiện',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
            ]),
          ),
          Switch(
            value: false,
            onChanged: (_) {},
            activeThumbColor: t.accent,
            inactiveTrackColor: t.skeleton,
          ),
        ]),
        const SizedBox(height: 10),
        Row(children: [
          Expanded(
            child: Text(
                'cani.vn/p/nguoi-dung (coming soon)',
                style: Theme.of(context)
                    .textTheme
                    .bodySmall
                    ?.copyWith(color: t.muted, fontStyle: FontStyle.italic)),
          ),
        ]),
        const SizedBox(height: 4),
        Text('Gợi ý: bật hồ sơ công khai để người tổ chức sự kiện tìm thấy bạn dễ hơn.',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
      ]),
    );
  }
}
