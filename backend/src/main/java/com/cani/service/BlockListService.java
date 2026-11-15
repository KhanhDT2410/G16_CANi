package com.cani.service;

import com.cani.dto.block.BlockUserRequestDto;
import com.cani.dto.block.BlockedUserResponseDto;
import com.cani.entity.BlockList;
import com.cani.entity.User;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.BlockListRepository;
import com.cani.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockListService {

    private final BlockListRepository blockListRepository;
    private final UserRepository userRepository;

    /**
     * UC-39: Xem danh sách chặn của người dùng hiện tại
     */
    @Transactional(readOnly = true)
    public List<BlockedUserResponseDto> getBlockedList(UUID blockerId) {
        log.info("Lấy danh sách chặn cho blockerId: {}", blockerId);
        List<BlockList> blockLists = blockListRepository.findByBlockerIdWithDetails(blockerId);
        return blockLists.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * UC-38: Thêm một người dùng (Event Organizer / Worker) vào danh sách chặn
     */
    @Transactional
    public BlockedUserResponseDto blockUser(UUID blockerId, BlockUserRequestDto request) {
        UUID blockedId = request.getBlockedUserId();
        log.info("Thực hiện chặn: blockerId={} -> blockedId={}", blockerId, blockedId);

        if (blockerId.equals(blockedId)) {
            throw new BadRequestException("Bạn không thể tự chặn chính mình");
        }

        if (blockListRepository.existsByBlocker_UserIdAndBlocked_UserId(blockerId, blockedId)) {
            throw new BadRequestException("Người dùng này đã có trong danh sách chặn của bạn");
        }

        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", blockerId));
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", blockedId));

        BlockList blockList = BlockList.builder()
                .blocker(blocker)
                .blocked(blocked)
                .reason(request.getReason() != null ? request.getReason().trim() : null)
                .build();

        BlockList saved = blockListRepository.save(blockList);
        log.info("Đã chặn thành công: blockId={}", saved.getBlockId());
        return mapToResponseDto(saved);
    }

    /**
     * UC-39: Bỏ chặn một người dùng
     */
    @Transactional
    public void unblockUser(UUID blockerId, UUID blockedId) {
        log.info("Thực hiện bỏ chặn: blockerId={} -> blockedId={}", blockerId, blockedId);

        BlockList blockList = blockListRepository.findByBlocker_UserIdAndBlocked_UserId(blockerId, blockedId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng này trong danh sách chặn của bạn"));

        blockListRepository.delete(blockList);
        log.info("Đã bỏ chặn thành công cho blockedId: {}", blockedId);
    }

    /**
     * Kiểm tra hai người dùng có ai chặn người kia hay không (phục vụ Dispatch & Matching)
     */
    @Transactional(readOnly = true)
    public boolean isEitherBlocked(UUID userAId, UUID userBId) {
        return blockListRepository.existsByBlocker_UserIdAndBlocked_UserId(userAId, userBId)
                || blockListRepository.existsByBlocker_UserIdAndBlocked_UserId(userBId, userAId);
    }

    private BlockedUserResponseDto mapToResponseDto(BlockList blockList) {
        User blocked = blockList.getBlocked();
        return BlockedUserResponseDto.builder()
                .blockId(blockList.getBlockId())
                .blockedUserId(blocked.getUserId())
                .fullName(blocked.getFullName())
                .email(blocked.getEmail())
                .phone(blocked.getPhone())
                .role(blocked.getRole())
                .avatarUrl(blocked.getAvatarUrl())
                .reason(blockList.getReason())
                .blockedAt(blockList.getCreatedAt())
                .build();
    }
}
