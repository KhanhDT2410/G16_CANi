import React from 'react';

/**
 * SkeletonCard - Hiệu ứng placeholder lấp lánh (shimmer) khi đang fetch dữ liệu,
 * giúp người dùng không cảm thấy giật lag hay màn hình trống trơn.
 */
export const SkeletonCard = ({ count = 3, height = '160px' }) => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', width: '100%' }}>
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className="skeleton"
          style={{
            height,
            width: '100%',
            borderRadius: '12px',
          }}
        />
      ))}
    </div>
  );
};

export default SkeletonCard;
