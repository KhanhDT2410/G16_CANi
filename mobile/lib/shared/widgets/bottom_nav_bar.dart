import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:cani/app/cani_tokens.dart';

/// Item descriptor for [BottomNavBar].
class BottomNavItem {
  final IconData icon;
  final IconData selectedIcon;
  final String label;
  final int badgeCount;

  const BottomNavItem({
    required this.icon,
    required this.selectedIcon,
    required this.label,
    this.badgeCount = 0,
  });
}

/// A modern floating capsule (pill) bottom navigation bar with morphing animations.
///
/// Expands the selected tab into a pill shape showing icon + label, while
/// collapsing inactive tabs to subtle icons. Fully adapts between Light and Dark themes.
class BottomNavBar extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;
  final List<BottomNavItem> items;
  final Color? activeColor;
  final Color? backgroundColor;

  const BottomNavBar({
    super.key,
    required this.currentIndex,
    required this.onTap,
    required this.items,
    this.activeColor,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final effectiveActiveColor = activeColor ?? tokens.brand;
    final effectiveBgColor = backgroundColor ?? tokens.surface;

    return SafeArea(
      top: false,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeInOut,
        height: 64,
        margin: const EdgeInsets.fromLTRB(22, 0, 22, 12),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        decoration: BoxDecoration(
          color: effectiveBgColor,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: tokens.border,
            width: 1.2,
          ),
          boxShadow: [
            BoxShadow(
              color: isDark
                  ? const Color(0x66000000)
                  : const Color(0x1F0F172A),
              blurRadius: 20,
              offset: Offset(0, isDark ? 8 : 6),
            ),
          ],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: List.generate(items.length, (index) {
            final item = items[index];
            final isSelected = currentIndex == index;

            return _PillNavItem(
              item: item,
              isSelected: isSelected,
              activeColor: effectiveActiveColor,
              onTap: () {
                if (!isSelected) {
                  HapticFeedback.selectionClick();
                  onTap(index);
                }
              },
            );
          }),
        ),
      ),
    );
  }
}

class _PillNavItem extends StatelessWidget {
  final BottomNavItem item;
  final bool isSelected;
  final Color activeColor;
  final VoidCallback onTap;

  const _PillNavItem({
    required this.item,
    required this.isSelected,
    required this.activeColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      splashColor: Colors.transparent,
      highlightColor: Colors.transparent,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
        padding: EdgeInsets.symmetric(
          horizontal: isSelected ? 16 : 14,
          vertical: 9,
        ),
        decoration: BoxDecoration(
          color: isSelected ? activeColor : Colors.transparent,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildIcon(context),
            if (isSelected)
              Padding(
                padding: const EdgeInsets.only(left: 7),
                child: Text(
                  item.label,
                  style: const TextStyle(
                    fontFamily: 'Be Vietnam Pro',
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                    letterSpacing: -0.2,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildIcon(BuildContext context) {
    final inactiveColor = Theme.of(context).cani.textMuted;

    final iconWidget = Icon(
      isSelected ? item.selectedIcon : item.icon,
      size: isSelected ? 20 : 21,
      color: isSelected ? Colors.white : inactiveColor,
    );

    if (item.badgeCount > 0 && !isSelected) {
      return Badge(
        label: Text(
          '${item.badgeCount}',
          style: const TextStyle(
            fontSize: 9.5,
            fontWeight: FontWeight.w800,
            color: Colors.white,
          ),
        ),
        backgroundColor: const Color(0xFFEF4444),
        child: iconWidget,
      );
    }

    return iconWidget;
  }
}
