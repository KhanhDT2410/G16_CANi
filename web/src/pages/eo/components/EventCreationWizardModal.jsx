import React, { useMemo, useState } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import {
  ROLES,
  WAGE_RECOMMENDATIONS_MAP,
  calculateShiftRiskAnalysis,
  normalizeChecklistItems,
  serializeChecklistItems,
} from '../hooks/useShifts';
import { getCoordinatesFromAddress } from '../utils/geoUtils';
import VenueLocationPicker from './VenueLocationPicker';
import ShiftChecklistEditor from './ShiftChecklistEditor';

/**
 * EventCreationWizardModal
 * Quy trình liên hoàn 3 bước (Clean Enterprise UI - No Icons):
 * 1. Khởi tạo Sự kiện & Địa điểm
 * 2. Thiết lập Danh sách Ca làm việc (Shifts) kèm gợi ý lương, đánh giá khó tìm nhân sự & Checklist
 * 3. Xác nhận Địa chỉ tổ chức cụ thể & Lưu Bản Nháp / Kích Hoạt Tìm Kiếm Ngay -> Mở trang Detail Sự Kiện
 */
const EventCreationWizardModal = ({
  isOpen,
  onClose,
  onSuccess,
  onEventCreated,
  newEvent,
  setNewEvent,
  onGetCurrentLocation,
  locating,
  skillTaxonomy = [],
}) => {
  const skillNameMap = useMemo(() => {
    const map = {};
    (Array.isArray(skillTaxonomy) ? skillTaxonomy : []).forEach((sk) => {
      if (sk && sk.skillId) map[sk.skillId] = sk.name;
    });
    return map;
  }, [skillTaxonomy]);
  const [currentStep, setCurrentStep] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [createdEvent, setCreatedEvent] = useState(null);
  const [step1Errors, setStep1Errors] = useState({});
  
  // State upload ảnh từ thiết bị
  const [uploadingEventImg, setUploadingEventImg] = useState(false);
  const [uploadingShiftImg, setUploadingShiftImg] = useState(false);
  const eventFileInputRef = React.useRef(null);
  const shiftFileInputRef = React.useRef(null);

  // Danh sách các ca làm việc (Shifts) đã thêm vào sự kiện này
  const [shiftsList, setShiftsList] = useState([]);
  const [isAddingShift, setIsAddingShift] = useState(true);
  const [editingShiftIndex, setEditingShiftIndex] = useState(null);
  const [wageMode, setWageMode] = useState('HOURLY'); // 'HOURLY' | 'PER_SHIFT'
  const [shiftPackageWage, setShiftPackageWage] = useState(200000);

  // Helper định dạng date -> string cho input datetime-local (YYYY-MM-DDTHH:mm)
  const toDateTimeInputVal = (date) => {
    if (!date) return '';
    const d = new Date(date);
    if (isNaN(d.getTime())) return '';
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  };

  const formatDateTimeDisplay = (date) => {
    if (!date) return 'Chưa xác định';
    const d = new Date(date);
    if (isNaN(d.getTime())) return 'Chưa xác định';
    return `${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} ngày ${d.toLocaleDateString('vi-VN')}`;
  };

  // State cho Form tạo ca hiện tại
  const [currentShift, setCurrentShift] = useState({
    jobType: ROLES[0],
    description: 'Hỗ trợ công tác đón tiếp khách, hướng dẫn vị trí và điều phối sự kiện theo phân công.',
    startTime: newEvent.startTime || '2026-11-20T08:00',
    endTime: '2026-11-20T12:00',
    requiredWorkers: 4,
    hourlyWage: 50000,
    allowance: 20000,
    maxDistanceKm: 15,
    imageUrl: '',
    requiredSkills: [],
    checklistItems: [
      { id: 'chk_1', title: 'Có mặt trước 20 phút nhận thẻ và vị trí', isRequiredEvidence: false },
      { id: 'chk_2', title: 'Đồng phục chỉn chu theo quy định', isRequiredEvidence: true },
      { id: 'chk_3', title: 'Quét mã QR Check-in đầu ca', isRequiredEvidence: false },
      { id: 'chk_4', title: 'Báo cáo quản lý trước khi kết thúc ca', isRequiredEvidence: false },
    ],
  });

  // Xử lý upload ảnh sự kiện từ thiết bị local
  const handleEventImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      showToast('Vui lòng chọn định dạng file ảnh (JPG, PNG, WEBP, GIF)', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showToast('Dung lượng ảnh không được vượt quá 5MB', 'error');
      return;
    }
    setUploadingEventImg(true);
    try {
      const res = await eoService.uploadImage(file);
      const url = res.url || res;
      setNewEvent((prev) => ({ ...prev, imageUrl: url }));
      showToast('Tải ảnh poster sự kiện thành công!', 'success');
    } catch (err) {
      showToast('Lỗi khi tải ảnh: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setUploadingEventImg(false);
      if (eventFileInputRef.current) eventFileInputRef.current.value = '';
    }
  };

  // Xử lý upload ảnh ca làm việc / đồng phục từ thiết bị local
  const handleShiftImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      showToast('Vui lòng chọn định dạng file ảnh (JPG, PNG, WEBP, GIF)', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showToast('Dung lượng ảnh không được vượt quá 5MB', 'error');
      return;
    }
    setUploadingShiftImg(true);
    try {
      const res = await eoService.uploadImage(file);
      const url = res.url || res;
      setCurrentShift((prev) => ({ ...prev, imageUrl: url }));
      showToast('Tải ảnh minh họa ca làm việc thành công!', 'success');
    } catch (err) {
      showToast('Lỗi khi tải ảnh: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setUploadingShiftImg(false);
      if (shiftFileInputRef.current) shiftFileInputRef.current.value = '';
    }
  };

  // Tính số giờ làm việc của ca hiện tại
  const getDurationHours = (start, end) => {
    try {
      const s = new Date(start);
      const e = new Date(end);
      const diff = (e - s) / (1000 * 60 * 60);
      return diff > 0 ? parseFloat(diff.toFixed(1)) : 4;
    } catch {
      return 4;
    }
  };

  // Nạp 1 ca đã tạo lên Form để người dùng chỉnh sửa lại Vị trí, Mô tả, Lương, Đầu việc Checklist
  const handleStartEditShift = (shift, index) => {
    const sTime = toDateTimeInputVal(shift.startTime) || (newEvent.startTime || '2026-11-20T08:00');
    const eTime = toDateTimeInputVal(shift.endTime) || '2026-11-20T12:00';
    const dur = getDurationHours(sTime, eTime);
    const hWage = Number(shift.hourlyWage || 50000);
    const parsedChecklist = normalizeChecklistItems(shift.checklistItems || shift.checklist);

    setEditingShiftIndex(index);
    setWageMode('HOURLY');
    setShiftPackageWage(Math.round(hWage * dur));
    setCurrentShift({
      jobType: shift.jobType || '',
      description: shift.description || '',
      startTime: sTime,
      endTime: eTime,
      requiredWorkers: Number(shift.requiredWorkers || 2),
      hourlyWage: hWage,
      allowance: Number(shift.allowance || 0),
      maxDistanceKm: Number(shift.maxDistanceKm || 15),
      imageUrl: shift.imageUrl || '',
      requiredSkills: Array.isArray(shift.requiredSkills) ? shift.requiredSkills : [],
      checklistItems: parsedChecklist,
    });
    setIsAddingShift(true);
  };

  // Chia Ca Nhanh: Chỉ chia sẵn khung thời gian cho các ca, để trống checklist/tên đầu việc để người dùng tự chỉnh sửa từng ca
  const handleQuickSplit = async (splitCount) => {
    const evtStart = new Date(createdEvent?.startTime || newEvent.startTime);
    const evtEnd = new Date(createdEvent?.endTime || newEvent.endTime);
    if (isNaN(evtStart.getTime()) || isNaN(evtEnd.getTime()) || evtStart >= evtEnd) {
      showToast('Thời gian sự kiện chưa hợp lệ để thực hiện chia ca nhanh', 'error');
      return;
    }
    if (!createdEvent?.eventId) {
      showToast('Không tìm thấy mã sự kiện để chia ca', 'error');
      return;
    }

    const totalMs = evtEnd.getTime() - evtStart.getTime();
    const slotMs = totalMs / splitCount;

    setSubmitting(true);
    try {
      const safeWage = Math.max(20000, Number(currentShift.hourlyWage || 50000));
      const safeAllowance = Math.max(0, Number(currentShift.allowance || 0));
      const safeWorkers = Math.max(1, Number(currentShift.requiredWorkers || 2));

      const createdSlots = [];
      for (let i = 0; i < splitCount; i++) {
        const slotStart = new Date(evtStart.getTime() + i * slotMs);
        const slotEnd = new Date(evtStart.getTime() + (i + 1) * slotMs);
        const defaultRole = ROLES[i % ROLES.length] || ROLES[0];
        const payload = {
          jobType: defaultRole,
          description: `Ca ${i + 1}/${splitCount} - Vui lòng cập nhật nhiệm vụ và đầu việc cho ca này.`,
          requiredWorkers: safeWorkers,
          hourlyWage: safeWage,
          allowance: safeAllowance,
          maxDistanceKm: Number(currentShift.maxDistanceKm || 15),
          imageUrl: null,
          requiredSkills: [],
          checklist: '[]',
          startTime: slotStart.toISOString(),
          endTime: slotEnd.toISOString(),
        };
        const slotHours = getDurationHours(slotStart, slotEnd);
        const slotCost = slotHours * safeWorkers * safeWage + safeAllowance;
        const addedShift = await eoService.addShiftToEvent(createdEvent.eventId, payload);
        createdSlots.push({
          ...addedShift,
          totalCost: Number(addedShift?.totalCost) || slotCost,
          checklist: '[]',
          checklistItems: [],
          isQuickSplitDraft: true,
        });
      }

      const firstNewIndex = shiftsList.length;
      const nextList = [...shiftsList, ...createdSlots];
      setShiftsList(nextList);

      // Mở ngay Ca #1 vừa chia để người dùng tự điền Vị trí, Mô tả và Checklist đầu việc
      if (createdSlots.length > 0) {
        handleStartEditShift(createdSlots[0], firstNewIndex);
      }

      showToast(
        `Đã chia khung giờ thành ${splitCount} ca! Hãy nhập tên vị trí và các đầu việc cho từng ca bên dưới.`,
        'success'
      );
    } catch (err) {
      showToast('Lỗi khi chia ca nhanh: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  const currentDurationHours = getDurationHours(currentShift.startTime, currentShift.endTime);
  const currentShiftTotal =
    currentDurationHours *
      Math.max(1, Number(currentShift.requiredWorkers || 1)) *
      Math.max(0, Number(currentShift.hourlyWage || 0)) +
    Math.max(0, Number(currentShift.allowance || 0));

  const riskAnalysis = calculateShiftRiskAnalysis(currentShift);

  // Đổi vai trò cho ca hiện tại kèm gợi ý mức lương
  const handleRoleChange = (role) => {
    const rec = WAGE_RECOMMENDATIONS_MAP[role];
    const nextHourly = rec ? rec.defaultWage : currentShift.hourlyWage;
    setCurrentShift((prev) => ({
      ...prev,
      jobType: role,
      hourlyWage: nextHourly,
    }));
    if (wageMode === 'PER_SHIFT') {
      setShiftPackageWage(Math.round(Number(nextHourly || 50000) * currentDurationHours));
    }
  };

  const handleSwitchWageMode = (mode) => {
    setWageMode(mode);
    if (mode === 'PER_SHIFT') {
      setShiftPackageWage(Math.round(Number(currentShift.hourlyWage || 50000) * currentDurationHours));
    }
  };

  const handlePackageWageChange = (val) => {
    const pkg = Math.max(0, parseFloat(val) || 0);
    setShiftPackageWage(pkg);
    const computedHourly = Math.max(0, Math.round(pkg / Math.max(0.5, currentDurationHours)));
    setCurrentShift((prev) => ({
      ...prev,
      hourlyWage: computedHourly,
    }));
  };

  const handleShiftTimeChange = (field, value) => {
    const nextStart = field === 'startTime' ? value : currentShift.startTime;
    const nextEnd = field === 'endTime' ? value : currentShift.endTime;
    const nextDur = getDurationHours(nextStart, nextEnd);
    if (wageMode === 'PER_SHIFT') {
      const computedHourly = Math.max(20000, Math.round(Number(shiftPackageWage || 0) / Math.max(0.5, nextDur)));
      setCurrentShift((prev) => ({
        ...prev,
        [field]: value,
        hourlyWage: computedHourly,
      }));
    } else {
      setCurrentShift((prev) => ({
        ...prev,
        [field]: value,
      }));
      setShiftPackageWage(Math.round(Number(currentShift.hourlyWage || 0) * nextDur));
    }
  };

  // Hàm kiểm tra chuỗi có phải gõ linh tinh / spam / keyboard mash không
  const isGibberishOrSpam = (text) => {
    if (!text) return false;
    const str = text.trim().toLowerCase();
    if (str.length < 3) return false;

    // 1. Ký tự đơn lặp lại liên tiếp >= 4 lần (ví dụ: aaaaa, 11111)
    if (/(.)\1{3,}/.test(str)) return true;

    // 2. Cặp ký tự lặp lại >= 3 lần (ví dụ: ababab, cdcdcd)
    if (/(.{2})\1{2,}/.test(str)) return true;

    // Tách từ theo khoảng trắng
    const words = str.split(/\s+/).filter(Boolean);

    // 3. Lặp từ liên tiếp >= 3 lần (ví dụ: "ád ád ád", "test test test")
    let consecutiveWord = null;
    let consecutiveCount = 0;
    for (const w of words) {
      if (w === consecutiveWord) {
        consecutiveCount++;
        if (consecutiveCount >= 3) return true;
      } else {
        consecutiveWord = w;
        consecutiveCount = 1;
      }
    }

    // 4. Các mẫu cào phím (Keyboard mash) phổ biến (Telex / Qwerty home row)
    const MASH_PATTERNS = /(asdasd|sdasd|ádasd|sdada|adasd|sdfsdf|dfghj|fghjk|ghjkl|qwerty|zxcvb)/i;
    if (MASH_PATTERNS.test(str)) return true;

    // 5. Kiểm tra tần suất từ chiếm ưu thế bất thường (ví dụ: "ádas ád ád ád ádasd ád")
    if (words.length >= 4) {
      const counts = {};
      for (const w of words) counts[w] = (counts[w] || 0) + 1;
      const maxCount = Math.max(...Object.values(counts));
      if (maxCount / words.length >= 0.45) return true;
    }

    // 6. Độ đa dạng ký tự (Character Diversity / Entropy) trên chuỗi chữ cái
    const cleanLetters = str.replace(/[^a-zàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]/gi, '');
    if (cleanLetters.length >= 10) {
      const uniqueChars = new Set(cleanLetters.split('')).size;
      const ratio = uniqueChars / cleanLetters.length;
      if (uniqueChars <= 4 || ratio < 0.30) {
        return true;
      }
    }

    // 7. Nguyên âm tiếng Việt & tiếng Anh
    const VOWELS = /[aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]/i;

    // Kiểm tra từng từ: nếu từ dài >= 4 ký tự nhưng KHÔNG chứa bất kỳ nguyên âm nào (và không phải toàn số)
    const hasVowellessLongWord = words.some((w) => {
      if (/^\d+$/.test(w)) return false;
      const cleanWord = w.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '');
      return cleanWord.length >= 4 && !VOWELS.test(cleanWord);
    });
    if (hasVowellessLongWord) return true;

    // 8. Chuỗi phụ âm liên tiếp >= 5 ký tự không ngắt quãng (như sdfdsfsdfsd, asdfgh)
    const CONSONANTS_RUN = /[^aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ\s\d,.\-_/()]{5,}/i;
    if (CONSONANTS_RUN.test(str)) return true;

    // 9. Chuỗi tổng thể >= 6 ký tự nhưng không có nguyên âm nào
    const nonSpace = str.replace(/\s+/g, '');
    if (nonSpace.length >= 6 && !VOWELS.test(nonSpace) && !/^\d+$/.test(nonSpace)) {
      return true;
    }

    return false;
  };

  const validateStep1 = (data) => {
    const errs = {};

    // 1. Tên sự kiện
    const name = data.name?.trim() || '';
    if (!name) {
      errs.name = 'Vui lòng nhập tên sự kiện.';
    } else if (name.length < 6) {
      errs.name = 'Tên sự kiện quá ngắn (tối thiểu 6 ký tự).';
    } else if (name.length > 255) {
      errs.name = 'Tên sự kiện không được vượt quá 255 ký tự.';
    } else if (isGibberishOrSpam(name)) {
      errs.name = 'Tên sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa (vui lòng nhập tên có nghĩa).';
    }

    // 2. Mô tả sự kiện (tùy chọn nhưng nếu nhập thì phải chuẩn)
    const desc = data.description?.trim() || '';
    if (desc) {
      if (desc.length < 3) {
        errs.description = 'Mô tả sự kiện quá ngắn (tối thiểu 3 ký tự nếu nhập).';
      } else if (isGibberishOrSpam(desc)) {
        errs.description = 'Mô tả sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.';
      }
    }

    // 3. Thời gian bắt đầu và kết thúc
    if (!data.startTime) {
      errs.startTime = 'Vui lòng chọn thời gian bắt đầu sự kiện.';
    }
    if (!data.endTime) {
      errs.endTime = 'Vui lòng chọn thời gian kết thúc sự kiện.';
    }

    if (data.startTime && data.endTime) {
      const start = new Date(data.startTime);
      const end = new Date(data.endTime);
      if (isNaN(start.getTime()) || isNaN(end.getTime())) {
        errs.time = 'Thời gian sự kiện không hợp lệ.';
      } else if (start >= end) {
        errs.time = 'Thời gian kết thúc phải diễn ra sau thời gian bắt đầu sự kiện.';
      } else if (end.getTime() - start.getTime() < 30 * 60 * 1000) {
        errs.time = 'Thời lượng sự kiện tối thiểu phải từ 30 phút trở lên.';
      }
    }

    // 4. Địa điểm tổ chức
    const addr = data.venueAddress?.trim() || '';
    if (!addr) {
      errs.venueAddress = 'Vui lòng chọn hoặc nhập địa chỉ tổ chức cụ thể.';
    }

    return errs;
  };

  // ==========================================
  // BƯỚC 1: TẠO SỰ KIỆN VÀ CHUYỂN SANG BƯỚC 2
  // ==========================================
  const handleStep1Submit = async (e) => {
    e.preventDefault();
    const errs = validateStep1(newEvent);
    if (Object.keys(errs).length > 0) {
      setStep1Errors(errs);
      const firstErrMsg = Object.values(errs)[0];
      showToast(firstErrMsg, 'error');
      return;
    }
    setStep1Errors({});

    const evtStart = new Date(newEvent.startTime);
    const evtEnd = new Date(newEvent.endTime);

    setSubmitting(true);
    try {
      let eventPayload = {
        ...newEvent,
        startTime: evtStart.toISOString(),
        endTime: evtEnd.toISOString(),
        eventDate: evtStart.toISOString().substring(0, 10),
      };
      // Tự động phân giải tọa độ GPS theo địa chỉ nếu có
      if (eventPayload.venueAddress?.trim()) {
        try {
          const coords = await getCoordinatesFromAddress(eventPayload.venueAddress);
          if (coords && coords.lat && coords.lng) {
            eventPayload.venueLat = coords.lat;
            eventPayload.venueLng = coords.lng;
            setNewEvent((prev) => ({ ...prev, venueLat: coords.lat, venueLng: coords.lng }));
          }
        } catch {
          // Bỏ qua lỗi mạng, sử dụng fallback an toàn
        }
      }
      eventPayload.venueLat = eventPayload.venueLat || 10.7328;
      eventPayload.venueLng = eventPayload.venueLng || 106.7214;

      let evt = createdEvent;
      if (!evt) {
        evt = await eoService.createEvent(eventPayload);
        setCreatedEvent(evt);
      }

      // Khởi tạo khung giờ cho ca làm đầu tiên khớp theo sự kiện
      const defaultShiftStart = toDateTimeInputVal(evtStart);
      const defaultShiftEndMs = Math.min(evtStart.getTime() + 4 * 3600 * 1000, evtEnd.getTime());
      const defaultShiftEnd = toDateTimeInputVal(new Date(defaultShiftEndMs));
      setCurrentShift((prev) => ({
        ...prev,
        startTime: defaultShiftStart,
        endTime: defaultShiftEnd,
      }));

      setCurrentStep(2);
      setIsAddingShift(true);
      showToast('Tạo sự kiện thành công! Hãy phân chia các ca làm việc trong sự kiện.', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi tạo sự kiện', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // ==========================================
  // BƯỚC 2: THÊM MỚI HOẶC CẬP NHẬT CA LÀM VIỆC (SHIFT) TRONG SỰ KIỆN
  // ==========================================
  const handleSaveShift = async (e) => {
    e?.preventDefault();
    if (!createdEvent?.eventId) {
      showToast('Không tìm thấy sự kiện. Vui lòng thử lại.', 'error');
      return;
    }

    if (!currentShift.jobType?.trim()) {
      showToast('Vui lòng nhập tên vị trí công việc cho ca làm', 'error');
      return;
    }

    if (!currentShift.description?.trim()) {
      showToast('Vui lòng nhập mô tả công việc cho ca làm', 'error');
      return;
    }

    if (currentShift.description.trim().length < 6) {
      showToast('Mô tả công việc quá ngắn (tối thiểu 6 ký tự)', 'error');
      return;
    }

    if (isGibberishOrSpam(currentShift.description)) {
      showToast('Mô tả công việc không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa!', 'error');
      return;
    }

    if (Number(currentShift.allowance || 0) < 0) {
      showToast('Phụ cấp thêm không được là số âm!', 'error');
      return;
    }

    if (Number(currentShift.hourlyWage || 0) < 20000) {
      showToast('Mức thù lao giờ tối thiểu là 20.000 VNĐ/giờ!', 'error');
      return;
    }

    if (Number(currentShift.requiredWorkers || 0) < 1) {
      showToast('Số lượng nhân sự cần tuyển tối thiểu là 1 người!', 'error');
      return;
    }

    // Kiểm tra ràng buộc thời gian ca làm việc trong sự kiện
    const sStart = new Date(currentShift.startTime);
    const sEnd = new Date(currentShift.endTime);
    const eStart = new Date(createdEvent?.startTime || newEvent.startTime);
    const eEnd = new Date(createdEvent?.endTime || newEvent.endTime);

    if (isNaN(sStart.getTime()) || isNaN(sEnd.getTime())) {
      showToast('Vui lòng chọn thời gian bắt đầu và kết thúc ca làm hợp lệ', 'error');
      return;
    }
    if (sStart >= sEnd) {
      showToast('Thời gian kết thúc ca làm việc phải sau thời gian bắt đầu ca', 'error');
      return;
    }
    if (sStart < eStart || sEnd > eEnd) {
      showToast(
        `Ca làm việc (${formatDateTimeDisplay(sStart)} - ${formatDateTimeDisplay(sEnd)}) phải nằm trọn trong thời gian sự kiện (${formatDateTimeDisplay(eStart)} - ${formatDateTimeDisplay(eEnd)})!`,
        'error'
      );
      return;
    }

    setSubmitting(true);
    try {
      const normalizedChecklist = normalizeChecklistItems(currentShift.checklistItems);
      const checklistJson = serializeChecklistItems(normalizedChecklist);
      const payload = {
        ...currentShift,
        jobType: currentShift.jobType.trim(),
        description: currentShift.description.trim(),
        requiredWorkers: Math.max(1, Number(currentShift.requiredWorkers || 1)),
        hourlyWage: Math.max(20000, Number(currentShift.hourlyWage || 50000)),
        allowance: Math.max(0, Number(currentShift.allowance || 0)),
        imageUrl: currentShift.imageUrl?.trim() || null,
        requiredSkills: Array.isArray(currentShift.requiredSkills) ? currentShift.requiredSkills : [],
        checklist: checklistJson,
        startTime: sStart.toISOString(),
        endTime: sEnd.toISOString(),
      };

      if (editingShiftIndex !== null && shiftsList[editingShiftIndex]) {
        const targetShift = shiftsList[editingShiftIndex];
        let updatedShift = {
          ...targetShift,
          ...payload,
          checklistItems: normalizedChecklist,
          totalCost: currentShiftTotal,
          isQuickSplitDraft: false,
        };
        if (targetShift.shiftId) {
          const apiRes = await eoService.updateShift(targetShift.shiftId, payload);
          updatedShift = {
            ...updatedShift,
            ...(apiRes || {}),
            checklist: checklistJson,
            checklistItems: normalizedChecklist,
            isQuickSplitDraft: false,
          };
        }

        const nextShiftsList = shiftsList.map((item, idx) =>
          idx === editingShiftIndex ? updatedShift : item
        );
        setShiftsList(nextShiftsList);

        // Nếu ca tiếp theo là ca vừa chia nhanh chưa có đầu việc, chuyển sang chỉnh sửa ca tiếp theo luôn
        const nextIdx = editingShiftIndex + 1;
        if (nextIdx < nextShiftsList.length && nextShiftsList[nextIdx].isQuickSplitDraft) {
          handleStartEditShift(nextShiftsList[nextIdx], nextIdx);
          showToast(
            `Đã cập nhật Ca #${editingShiftIndex + 1}! Mời bạn nhập tiếp thông tin cho Ca #${nextIdx + 1}.`,
            'success'
          );
        } else {
          setEditingShiftIndex(null);
          setIsAddingShift(false);
          showToast(`Đã cập nhật Ca #${editingShiftIndex + 1} (${updatedShift.jobType}) thành công!`, 'success');
        }
        return;
      }

      const addedShift = await eoService.addShiftToEvent(createdEvent.eventId, payload);
      setShiftsList((prev) => [
        ...prev,
        {
          ...addedShift,
          totalCost: Number(addedShift?.totalCost) || currentShiftTotal,
          checklist: checklistJson,
          checklistItems: normalizedChecklist,
        },
      ]);

      // Reset form để sẵn sàng tạo ca tiếp theo với giờ tiếp nối
      const nextRoleIndex = (shiftsList.length + 1) % ROLES.length;
      const nextRole = ROLES[nextRoleIndex];
      const nextRec = WAGE_RECOMMENDATIONS_MAP[nextRole];

      // Gợi ý giờ ca tiếp theo: bắt đầu từ khi ca trước kết thúc
      let nextStart = sEnd;
      let nextEnd = new Date(Math.min(nextStart.getTime() + 4 * 3600 * 1000, eEnd.getTime()));
      if (nextStart >= eEnd) {
        nextStart = eStart;
        nextEnd = new Date(Math.min(nextStart.getTime() + 4 * 3600 * 1000, eEnd.getTime()));
      }

      const nextDur = getDurationHours(nextStart, nextEnd);
      const nextWage = nextRec ? nextRec.defaultWage : 50000;
      setWageMode('HOURLY');
      setShiftPackageWage(Math.round(nextWage * nextDur));
      setEditingShiftIndex(null);

      setCurrentShift({
        jobType: nextRole,
        description: 'Phối hợp thực hiện nhiệm vụ theo kế hoạch tổ chức sự kiện.',
        startTime: toDateTimeInputVal(nextStart),
        endTime: toDateTimeInputVal(nextEnd),
        requiredWorkers: 2,
        hourlyWage: nextWage,
        allowance: 0,
        maxDistanceKm: 15,
        imageUrl: '',
        requiredSkills: [],
        checklistItems: [
          { id: 'chk_1', title: 'Có mặt đúng giờ quy định', isRequiredEvidence: false },
          { id: 'chk_2', title: 'Quét mã QR Check-in', isRequiredEvidence: false },
          { id: 'chk_3', title: 'Hoàn thành nhiệm vụ được giao', isRequiredEvidence: false },
        ],
      });

      setIsAddingShift(false);
      showToast(`Đã thêm ca "${addedShift.jobType}" thành công!`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi lưu ca làm', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // XÓA CA LÀM VIỆC (SHIFT)
  const handleDeleteShift = async (shiftId, index) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa ca làm việc này khỏi sự kiện?')) return;
    try {
      if (shiftId) {
        await eoService.cancelShift(shiftId, 'Ban tổ chức xóa ca');
      }
      setShiftsList((prev) => prev.filter((_, idx) => idx !== index));
      if (editingShiftIndex === index) {
        setEditingShiftIndex(null);
      }
      showToast('Đã xóa ca làm việc thành công!', 'info');
    } catch (err) {
      showToast('Lỗi khi xóa ca: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  // CHUYỂN SANG BƯỚC 3
  const handleProceedToStep3 = () => {
    if (shiftsList.length === 0) {
      showToast('Vui lòng thêm ít nhất một ca làm việc trước khi tiếp tục!', 'warning');
      return;
    }
    setCurrentStep(3);
  };

  // ==========================================
  // BƯỚC 3: LƯU BẢN NHÁP HOẶC KÍCH HOẠT TÌM KIẾM NGAY -> MỞ TRANG DETAIL SỰ KIỆN
  // ==========================================
  const computeShiftCost = (s) => {
    if (Number(s?.totalCost) > 0) return Number(s.totalCost);
    const hrs = getDurationHours(s?.startTime, s?.endTime);
    return (
      hrs *
        Math.max(1, Number(s?.requiredWorkers || 1)) *
        Math.max(0, Number(s?.hourlyWage || 0)) +
      Math.max(0, Number(s?.allowance || 0))
    );
  };

  const totalWorkers = shiftsList.reduce((sum, s) => sum + (Number(s.requiredWorkers) || 0), 0);
  const totalEstimatedCost = shiftsList.reduce((sum, s) => sum + computeShiftCost(s), 0);

  const buildFinalEventObject = (statusOverride) => ({
    ...newEvent,
    ...createdEvent,
    name: createdEvent?.name || newEvent.name,
    venueAddress: newEvent.venueAddress || createdEvent?.venueAddress,
    startTime: createdEvent?.startTime || newEvent.startTime,
    endTime: createdEvent?.endTime || newEvent.endTime,
    status: statusOverride || createdEvent?.status || 'DRAFT',
    shifts: shiftsList,
    totalShifts: shiftsList.length,
    totalRequiredWorkers: totalWorkers,
    totalConfirmedWorkers: 0,
    totalEstimatedCost,
  });

  const handleActivateMatching = async () => {
    if (!createdEvent?.eventId) return;
    setSubmitting(true);
    try {
      await eoService.publishEvent(createdEvent.eventId);
      const finalEvent = buildFinalEventObject('ACTIVE');
      showToast('Đã đăng tuyển và tự động tìm kiếm nhân sự thành công! Chuyển sang trang chi tiết sự kiện.', 'success');
      await onSuccess?.();
      handleClose();
      onEventCreated?.(finalEvent);
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi kích hoạt tìm kiếm', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveAsDraft = async () => {
    const finalEvent = buildFinalEventObject('DRAFT');
    showToast('Đã lưu sự kiện ở chế độ Bản nháp! Chuyển sang trang chi tiết sự kiện.', 'success');
    await onSuccess?.();
    handleClose();
    onEventCreated?.(finalEvent);
  };

  const handleClose = () => {
    setCurrentStep(1);
    setCreatedEvent(null);
    setShiftsList([]);
    setIsAddingShift(true);
    setStep1Errors({});
    onClose();
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: '780px', maxHeight: '92vh', overflowY: 'auto' }}>
        {/* THANH ĐIỀU HƯỚNG BƯỚC (STEPPER 3 BƯỚC GỌN GÀNG) */}
        <div style={{ marginBottom: '1.5rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Quy trình thiết lập sự kiện & ca làm việc
            </span>
            <button
              type="button"
              onClick={handleClose}
              style={{ background: 'none', border: 'none', fontSize: '1rem', cursor: 'pointer', color: '#64748b', fontWeight: 600 }}
            >
              Đóng
            </button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
            <div style={{ position: 'absolute', top: '50%', left: '12%', right: '12%', height: '2px', backgroundColor: '#e2e8f0', zIndex: 0 }} />
            
            {[
              { num: 1, label: '1. Sự Kiện & Địa Điểm' },
              { num: 2, label: '2. Các Ca Làm Việc' },
              { num: 3, label: '3. Xác Nhận & Kích Hoạt' },
            ].map((st) => {
              const isActive = currentStep === st.num;
              const isPassed = currentStep > st.num;
              return (
                <div
                  key={st.num}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '0.35rem',
                    zIndex: 1,
                    background: '#ffffff',
                    padding: '0 0.75rem',
                  }}
                >
                  <div
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '50%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: '0.875rem',
                      backgroundColor: isPassed ? '#10b981' : isActive ? '#2563eb' : '#f1f5f9',
                      color: isPassed || isActive ? '#ffffff' : '#64748b',
                      border: isActive ? '2px solid #93c5fd' : 'none',
                      transition: 'all 0.2s',
                    }}
                  >
                    {isPassed ? 'OK' : st.num}
                  </div>
                  <span
                    style={{
                      fontSize: '0.75rem',
                      fontWeight: isActive ? 700 : 500,
                      color: isActive ? '#2563eb' : isPassed ? '#10b981' : '#64748b',
                    }}
                  >
                    {st.label}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* ======================================================== */}
        {/* BƯỚC 1: THÔNG TIN SỰ KIỆN                               */}
        {/* ======================================================== */}
        {currentStep === 1 && (
          <form onSubmit={handleStep1Submit} className="animate-fade-in-up">
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
              Bước 1: Khởi Tạo Sự Kiện & Địa Điểm
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 1.25rem' }}>
              Nhập tên sự kiện và địa chỉ tổ chức cụ thể để hệ thống tìm kiếm nhân sự phù hợp.
            </p>

            <div className="eo-form-group">
              <label>Tên Sự Kiện *</label>
              <input
                type="text"
                required
                className="eo-form-input"
                style={{
                  borderColor: step1Errors.name ? '#ef4444' : undefined,
                  boxShadow: step1Errors.name ? '0 0 0 1px #ef4444' : undefined,
                }}
                placeholder="Ví dụ: Triển lãm Công nghệ TechExpo 2026"
                value={newEvent.name}
                onChange={(e) => {
                  setNewEvent({ ...newEvent, name: e.target.value });
                  if (step1Errors.name) setStep1Errors((prev) => ({ ...prev, name: null }));
                }}
                onBlur={() => {
                  const val = newEvent.name?.trim() || '';
                  if (!val) {
                    setStep1Errors((prev) => ({ ...prev, name: 'Vui lòng nhập tên sự kiện.' }));
                  } else if (val.length < 6) {
                    setStep1Errors((prev) => ({ ...prev, name: 'Tên sự kiện quá ngắn (tối thiểu 6 ký tự).' }));
                  } else if (isGibberishOrSpam(val)) {
                    setStep1Errors((prev) => ({ ...prev, name: 'Tên sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa (vui lòng nhập tên có nghĩa).' }));
                  }
                }}
              />
              {step1Errors.name && (
                <div style={{ color: '#ef4444', fontSize: '0.785rem', marginTop: '0.35rem', fontWeight: 600 }}>
                  {step1Errors.name}
                </div>
              )}
            </div>

            <div className="eo-form-group">
              <label>Mô Tả Quy Mô Sự Kiện</label>
              <textarea
                rows="2"
                className="eo-form-textarea"
                style={{
                  borderColor: step1Errors.description ? '#ef4444' : undefined,
                  boxShadow: step1Errors.description ? '0 0 0 1px #ef4444' : undefined,
                }}
                placeholder="Mô tả quy mô (ví dụ: nhỏ, vừa, lớn, 50 khách...)"
                value={newEvent.description}
                onChange={(e) => {
                  setNewEvent({ ...newEvent, description: e.target.value });
                  if (step1Errors.description) setStep1Errors((prev) => ({ ...prev, description: null }));
                }}
                onBlur={() => {
                  const val = newEvent.description?.trim() || '';
                  if (val) {
                    if (val.length < 3) {
                      setStep1Errors((prev) => ({ ...prev, description: 'Mô tả sự kiện quá ngắn (tối thiểu 3 ký tự nếu nhập).' }));
                    } else if (isGibberishOrSpam(val)) {
                      setStep1Errors((prev) => ({ ...prev, description: 'Mô tả sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.' }));
                    }
                  }
                }}
              />
              {step1Errors.description && (
                <div style={{ color: '#ef4444', fontSize: '0.785rem', marginTop: '0.35rem', fontWeight: 600 }}>
                  {step1Errors.description}
                </div>
              )}
            </div>

            {/* THỜI GIAN DIỄN RA TOÀN BỘ SỰ KIỆN */}
            <div className="eo-form-row">
              <div className="eo-form-group">
                <label>Thời Gian Bắt Đầu Sự Kiện *</label>
                <input
                  type="datetime-local"
                  required
                  className="eo-form-input"
                  style={{
                    borderColor: step1Errors.startTime || step1Errors.time ? '#ef4444' : undefined,
                  }}
                  value={newEvent.startTime || ''}
                  onChange={(e) => {
                    setNewEvent({ ...newEvent, startTime: e.target.value });
                    if (step1Errors.startTime || step1Errors.time) {
                      setStep1Errors((prev) => ({ ...prev, startTime: null, time: null }));
                    }
                  }}
                />
              </div>
              <div className="eo-form-group">
                <label>Thời Gian Kết Thúc Sự Kiện *</label>
                <input
                  type="datetime-local"
                  required
                  className="eo-form-input"
                  style={{
                    borderColor: step1Errors.endTime || step1Errors.time ? '#ef4444' : undefined,
                  }}
                  value={newEvent.endTime || ''}
                  onChange={(e) => {
                    setNewEvent({ ...newEvent, endTime: e.target.value });
                    if (step1Errors.endTime || step1Errors.time) {
                      setStep1Errors((prev) => ({ ...prev, endTime: null, time: null }));
                    }
                  }}
                />
              </div>
            </div>
            {step1Errors.time && (
              <div style={{ color: '#ef4444', fontSize: '0.785rem', marginTop: '-0.5rem', marginBottom: '0.75rem', fontWeight: 600 }}>
                {step1Errors.time}
              </div>
            )}

            {/* ẢNH BANNER / POSTER SỰ KIỆN (HIỂN THỊ TRONG 1 DIV RIÊNG BÊN DƯỚI CHO ẢNH TO RÕ) */}
            <div className="eo-form-group">
              <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>Ảnh Poster / Banner Sự Kiện (Tùy chọn)</span>
                <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Hỗ trợ JPG, PNG, WEBP, GIF (Tối đa 5MB)</span>
              </label>

              <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '0.5rem' }}>
                <input
                  type="file"
                  ref={eventFileInputRef}
                  accept="image/*"
                  style={{ display: 'none' }}
                  onChange={handleEventImageUpload}
                />
                <button
                  type="button"
                  disabled={uploadingEventImg}
                  onClick={() => eventFileInputRef.current?.click()}
                  className="eo-btn-secondary"
                  style={{
                    fontSize: '0.825rem',
                    padding: '0.5rem 1rem',
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                    cursor: uploadingEventImg ? 'not-allowed' : 'pointer',
                  }}
                >
                  {uploadingEventImg ? 'Đang tải ảnh lên...' : 'Tải ảnh từ máy tính'}
                </button>
                {!newEvent.imageUrl && (
                  <span style={{ fontSize: '0.78rem', color: '#64748b' }}>Chưa tải ảnh banner</span>
                )}
              </div>

              {newEvent.imageUrl && (
                <div
                  style={{
                    marginTop: '0.5rem',
                    borderRadius: '0.75rem',
                    border: '1px solid #cbd5e1',
                    overflow: 'hidden',
                    backgroundColor: '#f8fafc',
                    boxShadow: '0 2px 6px rgba(15, 23, 42, 0.06)',
                  }}
                >
                  <img
                    src={newEvent.imageUrl}
                    alt="Event Poster Preview"
                    style={{
                      width: '100%',
                      maxHeight: '240px',
                      objectFit: 'cover',
                      display: 'block',
                    }}
                    onError={(e) => {
                      e.target.style.display = 'none';
                    }}
                  />
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '0.5rem 0.9rem',
                      backgroundColor: '#ffffff',
                      borderTop: '1px solid #e2e8f0',
                    }}
                  >
                    <span style={{ fontSize: '0.82rem', color: '#15803d', fontWeight: 600 }}>
                      Đã tải ảnh poster sự kiện lên thành công
                    </span>
                    <button
                      type="button"
                      onClick={() => setNewEvent({ ...newEvent, imageUrl: '' })}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#dc2626',
                        fontSize: '0.82rem',
                        cursor: 'pointer',
                        fontWeight: 700,
                      }}
                    >
                      Gỡ ảnh
                    </button>
                  </div>
                </div>
              )}
            </div>

            <VenueLocationPicker
              address={newEvent.venueAddress}
              lat={newEvent.venueLat}
              lng={newEvent.venueLng}
              onLocationChange={({ address, lat, lng }) => {
                setNewEvent((prev) => ({
                  ...prev,
                  venueAddress: address,
                  venueLat: lat,
                  venueLng: lng,
                }));
                if (step1Errors.venueAddress) {
                  setStep1Errors((prev) => ({ ...prev, venueAddress: null }));
                }
              }}
              onGetCurrentLocation={onGetCurrentLocation}
              locating={locating}
              required
            />
            {step1Errors.venueAddress && (
              <div style={{ color: '#ef4444', fontSize: '0.785rem', marginTop: '0.35rem', fontWeight: 600 }}>
                {step1Errors.venueAddress}
              </div>
            )}

            <div className="eo-modal-footer">
              <button type="button" className="eo-btn-secondary" onClick={handleClose}>
                Hủy Bỏ
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="eo-btn-primary"
              >
                {submitting ? 'Đang tạo...' : 'Tiếp Tục'}
              </button>
            </div>
          </form>
        )}

        {/* ======================================================== */}
        {/* BƯỚC 2: TẠO & CHỈNH SỬA CA LÀM VIỆC (SHIFTS) */}
        {/* ======================================================== */}
        {currentStep === 2 && (
          <div className="animate-fade-in-up">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
                  Bước 2: Thiết Lập Danh Sách Ca Làm Việc
                </h3>
                <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0.25rem 0 0' }}>
                  Sự kiện: <strong>{createdEvent?.name || newEvent.name}</strong>. Các ca làm việc phải được phân bổ trong khung giờ sự kiện.
                </p>
              </div>

              {!isAddingShift && (
                <button
                  type="button"
                  className="eo-btn-primary"
                  style={{ fontSize: '0.85rem', padding: '0.5rem 1rem' }}
                  onClick={() => {
                    setEditingShiftIndex(null);
                    setIsAddingShift(true);
                  }}
                >
                  + Thêm Ca Mới
                </button>
              )}
            </div>

            {/* BANNER THỜI GIAN SỰ KIỆN CHA & CÔNG CỤ CHIA CA NHANH (CHỈ CHIA SẴN GIỜ, CHO PHÉP CHỈNH SỬA TỪNG CA) */}
            <div
              style={{
                backgroundColor: '#eff6ff',
                border: '1px solid #bfdbfe',
                borderRadius: '0.625rem',
                padding: '0.85rem 1rem',
                marginBottom: '1.25rem',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '0.75rem',
              }}
            >
              <div>
                <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#1e40af', marginBottom: '0.2rem' }}>
                  Khung giờ sự kiện:
                </div>
                <div style={{ fontSize: '0.875rem', color: '#1e293b', fontWeight: 600 }}>
                  {formatDateTimeDisplay(createdEvent?.startTime || newEvent.startTime)} đến{' '}
                  {formatDateTimeDisplay(createdEvent?.endTime || newEvent.endTime)}
                  <span style={{ color: '#2563eb', marginLeft: '0.4rem', fontWeight: 700 }}>
                    ({getDurationHours(createdEvent?.startTime || newEvent.startTime, createdEvent?.endTime || newEvent.endTime)} tiếng)
                  </span>
                </div>
                <div style={{ fontSize: '0.75rem', color: '#475569', marginTop: '0.2rem' }}>
                  Chia nhanh chỉ tạo sẵn mốc thời gian cho từng ca; bạn có thể bấm <strong>Chỉnh sửa ca</strong> để tự đặt vị trí & đầu việc.
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                <span style={{ fontSize: '0.75rem', color: '#475569', fontWeight: 600 }}>Chia nhanh thời gian:</span>
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => handleQuickSplit(2)}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#2563eb',
                    border: '1px solid #93c5fd',
                    borderRadius: '0.375rem',
                    padding: '0.4rem 0.75rem',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    cursor: submitting ? 'not-allowed' : 'pointer',
                  }}
                  title="Chia khung giờ sự kiện thành 2 mốc thời gian ca và mở chỉnh sửa cho từng ca"
                >
                  Chia 2 Ca
                </button>
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => handleQuickSplit(3)}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#2563eb',
                    border: '1px solid #93c5fd',
                    borderRadius: '0.375rem',
                    padding: '0.4rem 0.75rem',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    cursor: submitting ? 'not-allowed' : 'pointer',
                  }}
                  title="Chia khung giờ sự kiện thành 3 mốc thời gian ca và mở chỉnh sửa cho từng ca"
                >
                  Chia 3 Ca
                </button>
              </div>
            </div>

            {/* DANH SÁCH CÁC CA ĐÃ TẠO (CÓ NÚT CHỈNH SỬA LẠI TỪNG CA) */}
            {shiftsList.length > 0 && (
              <div style={{ marginBottom: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                <div style={{ fontSize: '0.875rem', fontWeight: 700, color: '#334155', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Danh sách ca đã thêm ({shiftsList.length} ca):</span>
                  <span style={{ color: '#2563eb' }}>
                    Tổng: {totalWorkers} nhân sự • Dự toán: {totalEstimatedCost.toLocaleString()} đ
                  </span>
                </div>

                {shiftsList.map((shift, idx) => {
                  const sHours = getDurationHours(shift.startTime, shift.endTime);
                  const isEditingThis = editingShiftIndex === idx && isAddingShift;
                  const parsedTasks = normalizeChecklistItems(shift.checklistItems || shift.checklist);
                  return (
                    <div
                      key={shift.shiftId || idx}
                      style={{
                        backgroundColor: isEditingThis ? '#eff6ff' : '#ffffff',
                        border: isEditingThis ? '2px solid #2563eb' : '1px solid #cbd5e1',
                        borderRadius: '0.625rem',
                        padding: '1rem',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.75rem',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
                        <div style={{ flex: 1, minWidth: '220px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.35rem', flexWrap: 'wrap' }}>
                            <span style={{ backgroundColor: '#eff6ff', color: '#1d4ed8', fontWeight: 700, fontSize: '0.85rem', padding: '0.2rem 0.5rem', borderRadius: '0.35rem' }}>
                              Ca #{idx + 1}
                            </span>
                            <strong style={{ fontSize: '1rem', color: '#0f172a' }}>{shift.jobType}</strong>
                            <span style={{ fontSize: '0.85rem', color: '#16a34a', fontWeight: 600 }}>
                              {shift.requiredWorkers} người x {Number(shift.hourlyWage || 0).toLocaleString()} đ/h
                            </span>
                            <span
                              style={{
                                fontSize: '0.75rem',
                                padding: '0.15rem 0.5rem',
                                borderRadius: '999px',
                                backgroundColor: parsedTasks.length > 0 ? '#f0fdf4' : '#fffbeb',
                                color: parsedTasks.length > 0 ? '#15803d' : '#b45309',
                                border: parsedTasks.length > 0 ? '1px solid #bbf7d0' : '1px solid #fde68a',
                                fontWeight: 600,
                              }}
                            >
                              {parsedTasks.length > 0 ? `${parsedTasks.length} đầu việc` : 'Chưa có đầu việc (Bấm Chỉnh sửa ca)'}
                            </span>
                          </div>

                          <div style={{ fontSize: '0.825rem', color: '#64748b', display: 'flex', flexWrap: 'wrap', gap: '0.85rem' }}>
                            <span>
                              Thời gian: {sHours}h (
                              {new Date(shift.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                              {new Date(shift.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})
                            </span>
                            <span>
                              Dự toán ca: <strong>{computeShiftCost(shift).toLocaleString()} đ</strong>
                            </span>
                            {shift.allowance > 0 && <span>Phụ cấp: {Number(shift.allowance).toLocaleString()} đ</span>}
                          </div>

                          {Array.isArray(shift.requiredSkills) && shift.requiredSkills.length > 0 && (
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginTop: '0.4rem' }}>
                              {shift.requiredSkills.map((skillId) => (
                                <span
                                  key={skillId}
                                  style={{
                                    fontSize: '0.72rem',
                                    padding: '0.15rem 0.5rem',
                                    borderRadius: '999px',
                                    backgroundColor: '#eff6ff',
                                    color: '#1d4ed8',
                                    border: '1px solid #bfdbfe',
                                    fontWeight: 600,
                                  }}
                                >
                                  {skillNameMap[skillId] || 'Kỹ năng yêu cầu'}
                                </span>
                              ))}
                            </div>
                          )}

                          {shift.description && (
                            <div style={{ fontSize: '0.8rem', color: '#475569', marginTop: '0.35rem', fontStyle: 'italic' }}>
                              &ldquo;{shift.description}&rdquo;
                            </div>
                          )}
                        </div>

                        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                          <button
                            type="button"
                            onClick={() => handleStartEditShift(shift, idx)}
                            style={{
                              backgroundColor: isEditingThis ? '#2563eb' : '#eff6ff',
                              color: isEditingThis ? '#ffffff' : '#1d4ed8',
                              border: '1px solid #93c5fd',
                              borderRadius: '0.375rem',
                              padding: '0.45rem 0.8rem',
                              fontSize: '0.8rem',
                              fontWeight: 700,
                              cursor: 'pointer',
                            }}
                            title="Mở form chỉnh sửa tên vị trí, thời gian, lương và đầu việc của ca này"
                          >
                            {isEditingThis ? 'Đang sửa ca này' : 'Chỉnh sửa ca'}
                          </button>

                          <button
                            type="button"
                            onClick={() => handleDeleteShift(shift.shiftId, idx)}
                            style={{
                              backgroundColor: '#fef2f2',
                              color: '#dc2626',
                              border: '1px solid #fecaca',
                              borderRadius: '0.375rem',
                              padding: '0.45rem 0.75rem',
                              fontSize: '0.8rem',
                              fontWeight: 600,
                              cursor: 'pointer',
                            }}
                            title="Xóa ca làm này khỏi sự kiện"
                          >
                            Xóa Ca
                          </button>
                        </div>
                      </div>

                      {/* Ảnh ca làm hiển thị trong 1 div riêng cho to rõ */}
                      {shift.imageUrl && (
                        <div
                          style={{
                            borderRadius: '0.5rem',
                            overflow: 'hidden',
                            border: '1px solid #e2e8f0',
                            backgroundColor: '#f8fafc',
                          }}
                        >
                          <img
                            src={shift.imageUrl}
                            alt={shift.jobType}
                            style={{
                              width: '100%',
                              maxHeight: '180px',
                              objectFit: 'cover',
                              display: 'block',
                            }}
                            onError={(e) => {
                              e.target.style.display = 'none';
                            }}
                          />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            {/* FORM TẠO HOẶC CHỈNH SỬA CA LÀM VIỆC (SHIFT FORM) */}
            {isAddingShift ? (
              <form
                onSubmit={handleSaveShift}
                style={{
                  backgroundColor: '#f8fafc',
                  padding: '1.25rem',
                  borderRadius: '0.75rem',
                  border: editingShiftIndex !== null ? '2px solid #2563eb' : '1.5px dashed #93c5fd',
                  marginBottom: '1.5rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                  <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700, color: '#1e40af' }}>
                    {editingShiftIndex !== null
                      ? `Đang Chỉnh Sửa Ca #${editingShiftIndex + 1} (Cập nhật Vị trí, Thù lao & Đầu việc)`
                      : `Nhập Thông Tin Ca Làm Việc ${shiftsList.length > 0 ? `Thứ ${shiftsList.length + 1}` : 'Đầu Tiên'}`}
                  </h4>
                  {shiftsList.length > 0 && (
                    <button
                      type="button"
                      onClick={() => {
                        setEditingShiftIndex(null);
                        setIsAddingShift(false);
                      }}
                      style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 600 }}
                    >
                      Thu Gọn
                    </button>
                  )}
                </div>

                <div className="eo-form-group">
                  <label>Vị Trí / Vai Trò Cần Tuyển *</label>
                  <input
                    type="text"
                    list="wizard-roles-list"
                    className="eo-form-input"
                    placeholder="Nhập vị trí hoặc chọn gợi ý (VD: Phục vụ bàn, Lễ tân, MC, Điều phối...)"
                    value={currentShift.jobType}
                    onChange={(e) => handleRoleChange(e.target.value)}
                    required
                  />
                  <datalist id="wizard-roles-list">
                    {ROLES.map((role) => (
                      <option key={role} value={role} />
                    ))}
                  </datalist>
                </div>

                {/* Mô tả công việc */}
                <div className="eo-form-group">
                  <label>Mô Tả Nhiệm Vụ Công Việc Trong Ca *</label>
                  <textarea
                    rows="2"
                    required
                    className="eo-form-textarea"
                    placeholder="VD: Hỗ trợ đón tiếp khách, hướng dẫn vị trí, điều phối micro..."
                    value={currentShift.description}
                    onChange={(e) => setCurrentShift({ ...currentShift, description: e.target.value })}
                  />
                </div>

                {/* Ảnh minh họa ca làm / đồng phục (XUỐNG 1 DIV RIÊNG CHO ẢNH TO RÕ) */}
                <div className="eo-form-group">
                  <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>Ảnh Minh Họa Ca Làm / Đồng Phục (Tùy chọn)</span>
                    <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Hỗ trợ JPG, PNG, WEBP, GIF (Tối đa 5MB)</span>
                  </label>

                  <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '0.5rem' }}>
                    <input
                      type="file"
                      ref={shiftFileInputRef}
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={handleShiftImageUpload}
                    />
                    <button
                      type="button"
                      disabled={uploadingShiftImg}
                      onClick={() => shiftFileInputRef.current?.click()}
                      className="eo-btn-secondary"
                      style={{
                        fontSize: '0.825rem',
                        padding: '0.5rem 1rem',
                        fontWeight: 600,
                        whiteSpace: 'nowrap',
                        cursor: uploadingShiftImg ? 'not-allowed' : 'pointer',
                      }}
                    >
                      {uploadingShiftImg ? 'Đang tải ảnh lên...' : 'Tải ảnh từ máy tính'}
                    </button>
                    {!currentShift.imageUrl && (
                      <span style={{ fontSize: '0.78rem', color: '#64748b' }}>Chưa chọn ảnh minh họa cho ca này</span>
                    )}
                  </div>

                  {currentShift.imageUrl && (
                    <div
                      style={{
                        marginTop: '0.5rem',
                        borderRadius: '0.625rem',
                        border: '1px solid #cbd5e1',
                        overflow: 'hidden',
                        backgroundColor: '#ffffff',
                      }}
                    >
                      <img
                        src={currentShift.imageUrl}
                        alt="Shift Preview"
                        style={{
                          width: '100%',
                          maxHeight: '220px',
                          objectFit: 'cover',
                          display: 'block',
                        }}
                        onError={(e) => {
                          e.target.style.display = 'none';
                        }}
                      />
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          padding: '0.45rem 0.85rem',
                          borderTop: '1px solid #e2e8f0',
                        }}
                      >
                        <span style={{ fontSize: '0.8rem', color: '#15803d', fontWeight: 600 }}>
                          Đã tải ảnh minh họa ca làm việc
                        </span>
                        <button
                          type="button"
                          onClick={() => setCurrentShift({ ...currentShift, imageUrl: '' })}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#dc2626',
                            fontSize: '0.8rem',
                            cursor: 'pointer',
                            fontWeight: 700,
                          }}
                        >
                          Gỡ ảnh
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                <div className="eo-form-row">
                  <div className="eo-form-group">
                    <label>Thời Gian Bắt Đầu *</label>
                    <input
                      type="datetime-local"
                      required
                      className="eo-form-input"
                      value={currentShift.startTime}
                      onChange={(e) => handleShiftTimeChange('startTime', e.target.value)}
                    />
                  </div>
                  <div className="eo-form-group">
                    <label>Thời Gian Kết Thúc *</label>
                    <input
                      type="datetime-local"
                      required
                      className="eo-form-input"
                      value={currentShift.endTime}
                      onChange={(e) => handleShiftTimeChange('endTime', e.target.value)}
                    />
                  </div>
                </div>

                {/* Cảnh báo trực quan nếu thời gian ca làm việc nằm ngoài thời gian sự kiện */}
                {(() => {
                  const sS = new Date(currentShift.startTime);
                  const sE = new Date(currentShift.endTime);
                  const evS = new Date(createdEvent?.startTime || newEvent.startTime);
                  const evE = new Date(createdEvent?.endTime || newEvent.endTime);
                  const isOutOfRange =
                    !isNaN(sS.getTime()) &&
                    !isNaN(sE.getTime()) &&
                    !isNaN(evS.getTime()) &&
                    !isNaN(evE.getTime()) &&
                    (sS < evS || sE > evE);
                  if (isOutOfRange) {
                    return (
                      <div
                        style={{
                          backgroundColor: '#fef2f2',
                          border: '1px solid #fecaca',
                          borderRadius: '0.375rem',
                          padding: '0.5rem 0.75rem',
                          fontSize: '0.8rem',
                          color: '#dc2626',
                          marginBottom: '0.75rem',
                          fontWeight: 600,
                        }}
                      >
                        Chú ý: Thời gian ca làm phải nằm trọn trong khung giờ sự kiện ({formatDateTimeDisplay(evS)} -{' '}
                        {formatDateTimeDisplay(evE)})
                      </div>
                    );
                  }
                  return null;
                })()}

                {/* CHO EO CHỌN TÍNH LƯƠNG THEO GIỜ HOẶC THEO CA */}
                <div className="eo-form-group" style={{ marginBottom: '0.6rem' }}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.5rem',
                      flexWrap: 'wrap',
                      gap: '0.5rem',
                    }}
                  >
                    <label style={{ margin: 0 }}>Hình Thức Tính Thù Lao *</label>
                    <div
                      style={{
                        display: 'inline-flex',
                        backgroundColor: '#e2e8f0',
                        padding: '3px',
                        borderRadius: '8px',
                      }}
                    >
                      <button
                        type="button"
                        onClick={() => handleSwitchWageMode('HOURLY')}
                        style={{
                          padding: '0.35rem 0.85rem',
                          fontSize: '0.78rem',
                          fontWeight: 700,
                          borderRadius: '6px',
                          border: 'none',
                          cursor: 'pointer',
                          backgroundColor: wageMode === 'HOURLY' ? '#2563eb' : 'transparent',
                          color: wageMode === 'HOURLY' ? '#ffffff' : '#334155',
                        }}
                      >
                        Tính theo giờ (VNĐ/giờ)
                      </button>
                      <button
                        type="button"
                        onClick={() => handleSwitchWageMode('PER_SHIFT')}
                        style={{
                          padding: '0.35rem 0.85rem',
                          fontSize: '0.78rem',
                          fontWeight: 700,
                          borderRadius: '6px',
                          border: 'none',
                          cursor: 'pointer',
                          backgroundColor: wageMode === 'PER_SHIFT' ? '#2563eb' : 'transparent',
                          color: wageMode === 'PER_SHIFT' ? '#ffffff' : '#334155',
                        }}
                      >
                        Tính theo ca (Trọn gói/ca)
                      </button>
                    </div>
                  </div>

                  <div className="eo-form-row" style={{ marginBottom: 0 }}>
                    <div className="eo-form-group" style={{ marginBottom: 0 }}>
                      <label>Số Lượng Nhân Sự Cần Tuyển *</label>
                      <input
                        type="number"
                        min="1"
                        required
                        className="eo-form-input"
                        value={currentShift.requiredWorkers}
                        onKeyDown={(e) => {
                          if (e.key === '-' || e.key === 'e') e.preventDefault();
                        }}
                        onChange={(e) =>
                          setCurrentShift({
                            ...currentShift,
                            requiredWorkers: Math.max(1, parseInt(e.target.value, 10) || 1),
                          })
                        }
                      />
                    </div>

                    {wageMode === 'HOURLY' ? (
                      <div className="eo-form-group" style={{ marginBottom: 0 }}>
                        <label>Mức Thù Lao Theo Giờ (VNĐ/giờ) *</label>
                        <input
                          type="number"
                          min="20000"
                          step="5000"
                          required
                          className="eo-form-input"
                          value={currentShift.hourlyWage}
                          onKeyDown={(e) => {
                            if (e.key === '-' || e.key === 'e') e.preventDefault();
                          }}
                          onChange={(e) => {
                            const val = Math.max(0, parseFloat(e.target.value) || 0);
                            setCurrentShift({
                              ...currentShift,
                              hourlyWage: val,
                            });
                            setShiftPackageWage(Math.round(val * currentDurationHours));
                          }}
                        />
                        <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                          = {Math.round(Number(currentShift.hourlyWage || 0) * currentDurationHours).toLocaleString()}đ / ca ({currentDurationHours} giờ)
                        </div>
                      </div>
                    ) : (
                      <div className="eo-form-group" style={{ marginBottom: 0 }}>
                        <label>Mức Thù Lao Trọn Gói Theo Ca (VNĐ/ca) *</label>
                        <input
                          type="number"
                          min="20000"
                          step="10000"
                          required
                          className="eo-form-input"
                          value={shiftPackageWage}
                          onKeyDown={(e) => {
                            if (e.key === '-' || e.key === 'e') e.preventDefault();
                          }}
                          onChange={(e) => handlePackageWageChange(e.target.value)}
                        />
                        <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                          Quy đổi tương đương: ~{Number(currentShift.hourlyWage || 0).toLocaleString()}đ/giờ ({currentDurationHours} giờ/ca)
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* WAGE RECOMMENDATION (GỢI Ý LƯƠNG THEO VỊ TRÍ) */}
                <div
                  style={{
                    backgroundColor: '#f0f9ff',
                    border: '1px solid #bae6fd',
                    borderRadius: '0.5rem',
                    padding: '0.75rem 1rem',
                    marginBottom: '0.85rem',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '0.5rem',
                  }}
                >
                  <div>
                    <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#0369a1' }}>
                      Gợi ý mức lương thị trường ({currentShift.jobType}):{' '}
                      {((riskAnalysis?.recommendation?.min) || 40000).toLocaleString()}đ - {((riskAnalysis?.recommendation?.max) || 60000).toLocaleString()}đ/giờ
                    </div>
                    <div style={{ fontSize: '0.75rem', color: '#475569', marginTop: '0.15rem' }}>
                      Tiêu chí: {riskAnalysis?.recommendation?.note || 'Tiêu chuẩn'} • Mức đề xuất tối ưu:{' '}
                      <strong>{((riskAnalysis?.recommendation?.defaultWage) || 50000).toLocaleString()}đ/giờ</strong>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      onClick={() => {
                        const minW = riskAnalysis?.recommendation?.min || 40000;
                        setCurrentShift((prev) => ({ ...prev, hourlyWage: minW }));
                        setShiftPackageWage(Math.round(minW * currentDurationHours));
                      }}
                      style={{
                        fontSize: '0.72rem',
                        padding: '0.25rem 0.55rem',
                        borderRadius: '4px',
                        border: '1px solid #7dd3fc',
                        background: '#ffffff',
                        color: '#0284c7',
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      Sàn: {((riskAnalysis?.recommendation?.min) || 40000).toLocaleString()}đ
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        const defW = riskAnalysis?.recommendation?.defaultWage || 50000;
                        setCurrentShift((prev) => ({ ...prev, hourlyWage: defW }));
                        setShiftPackageWage(Math.round(defW * currentDurationHours));
                      }}
                      style={{
                        fontSize: '0.72rem',
                        padding: '0.25rem 0.55rem',
                        borderRadius: '4px',
                        border: '1px solid #0284c7',
                        background: '#0284c7',
                        color: '#ffffff',
                        fontWeight: 700,
                        cursor: 'pointer',
                      }}
                    >
                      Khuyên dùng: {((riskAnalysis?.recommendation?.defaultWage) || 50000).toLocaleString()}đ
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        const maxW = riskAnalysis?.recommendation?.max || 60000;
                        setCurrentShift((prev) => ({ ...prev, hourlyWage: maxW }));
                        setShiftPackageWage(Math.round(maxW * currentDurationHours));
                      }}
                      style={{
                        fontSize: '0.72rem',
                        padding: '0.25rem 0.55rem',
                        borderRadius: '4px',
                        border: '1px solid #7dd3fc',
                        background: '#ffffff',
                        color: '#0284c7',
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      Thu hút nhanh: {((riskAnalysis?.recommendation?.max) || 60000).toLocaleString()}đ
                    </button>
                  </div>
                </div>

                <div className="eo-form-row">
                  <div className="eo-form-group">
                    <label>Phụ Cấp Thêm (VNĐ)</label>
                    <input
                      type="number"
                      min="0"
                      step="10000"
                      className="eo-form-input"
                      value={currentShift.allowance}
                      onKeyDown={(e) => {
                        if (e.key === '-' || e.key === 'e') e.preventDefault();
                      }}
                      onChange={(e) => {
                        const rawVal = parseFloat(e.target.value);
                        setCurrentShift({
                          ...currentShift,
                          allowance: isNaN(rawVal) ? 0 : Math.max(0, rawVal),
                        });
                      }}
                    />
                  </div>
                  <div className="eo-form-group">
                    <label>Bán Kính Quét Ứng Viên (km)</label>
                    <input
                      type="number"
                      min="1"
                      max="50"
                      className="eo-form-input"
                      value={currentShift.maxDistanceKm}
                      onKeyDown={(e) => {
                        if (e.key === '-' || e.key === 'e') e.preventDefault();
                      }}
                      onChange={(e) =>
                        setCurrentShift({
                          ...currentShift,
                          maxDistanceKm: Math.max(1, Math.min(50, parseFloat(e.target.value) || 15)),
                        })
                      }
                    />
                  </div>
                </div>

                {/* CẢNH BÁO KHÓ TÌM NHÂN SỰ (KHÔNG GHI RISK SCORE) */}
                <div
                  style={{
                    backgroundColor:
                      riskAnalysis.level === 'HIGH'
                        ? '#fef2f2'
                        : riskAnalysis.level === 'MEDIUM'
                        ? '#fffbeb'
                        : '#f0fdf4',
                    border:
                      riskAnalysis.level === 'HIGH'
                        ? '1px solid #fecaca'
                        : riskAnalysis.level === 'MEDIUM'
                        ? '1px solid #fde68a'
                        : '1px solid #bbf7d0',
                    borderRadius: '0.5rem',
                    padding: '0.75rem 1rem',
                    marginBottom: '1rem',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                    <span
                      style={{
                        fontSize: '0.85rem',
                        fontWeight: 700,
                        color:
                          riskAnalysis.level === 'HIGH'
                            ? '#dc2626'
                            : riskAnalysis.level === 'MEDIUM'
                            ? '#b45309'
                            : '#15803d',
                      }}
                    >
                      {riskAnalysis.level === 'HIGH'
                        ? 'Khó tìm nhân sự'
                        : riskAnalysis.level === 'MEDIUM'
                        ? 'Khá khó tìm nhân sự'
                        : 'Dễ tìm nhân sự'}
                    </span>

                    {riskAnalysis.level !== 'LOW' && (
                      <button
                        type="button"
                        onClick={() => {
                          const nextWage = Math.max(currentShift.hourlyWage, riskAnalysis.recommendation.defaultWage);
                          setCurrentShift((prev) => ({
                            ...prev,
                            hourlyWage: nextWage,
                            maxDistanceKm: Math.max(prev.maxDistanceKm, 15),
                          }));
                          setShiftPackageWage(Math.round(nextWage * currentDurationHours));
                        }}
                        style={{
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          padding: '0.25rem 0.65rem',
                          borderRadius: '4px',
                          border: 'none',
                          background: riskAnalysis.level === 'HIGH' ? '#dc2626' : '#d97706',
                          color: '#ffffff',
                          cursor: 'pointer',
                        }}
                      >
                        Tối ưu ngay để dễ tìm nhân sự
                      </button>
                    )}
                  </div>

                  {riskAnalysis.reasons.length > 0 && (
                    <ul style={{ margin: '0.4rem 0 0', paddingLeft: '1.1rem', fontSize: '0.78rem', color: '#475569' }}>
                      {riskAnalysis.reasons.map((reason, rIdx) => (
                        <li key={rIdx}>{reason}</li>
                      ))}
                    </ul>
                  )}
                </div>

                {/* KỸ NĂNG BẮT BUỘC CHO CA NÀY */}
                <div className="eo-form-group" style={{ marginBottom: '1rem' }}>
                  <label>Kỹ Năng Bắt Buộc (chọn nhiều)</label>
                  {skillTaxonomy.length > 0 ? (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', marginTop: '0.35rem' }}>
                      {skillTaxonomy.map((sk) => {
                        const checked =
                          Array.isArray(currentShift.requiredSkills) &&
                          currentShift.requiredSkills.includes(sk.skillId);
                        return (
                          <button
                            key={sk.skillId}
                            type="button"
                            onClick={() => {
                              const base = Array.isArray(currentShift.requiredSkills)
                                ? currentShift.requiredSkills
                                : [];
                              const next = checked
                                ? base.filter((id) => id !== sk.skillId)
                                : [...base, sk.skillId];
                              setCurrentShift({ ...currentShift, requiredSkills: next });
                            }}
                            style={{
                              fontSize: '0.76rem',
                              padding: '0.25rem 0.6rem',
                              borderRadius: '0.45rem',
                              border: checked ? '1.5px solid #2563eb' : '1px solid #cbd5e1',
                              backgroundColor: checked ? '#dbeafe' : '#ffffff',
                              color: checked ? '#1d4ed8' : '#475569',
                              fontWeight: checked ? 700 : 500,
                              cursor: 'pointer',
                            }}
                          >
                            {checked ? '✓ ' : ''}
                            {sk.name}
                          </button>
                        );
                      })}
                    </div>
                  ) : (
                    <div style={{ fontSize: '0.74rem', color: '#94a3b8', marginTop: '0.3rem' }}>
                      Đang tải danh mục kỹ năng hoặc chưa có danh mục kỹ năng để chọn.
                    </div>
                  )}
                  <div style={{ fontSize: '0.74rem', color: '#64748b', marginTop: '0.3rem' }}>
                    Hệ thống chỉ mời ứng viên có đúng kỹ năng này (lọc cứng trong matching). Để trống nếu ca phổ thông không yêu cầu kỹ năng đặc thù.
                  </div>
                </div>

                {/* CHECKLIST ĐẦU VIỆC CHO CA NÀY (CÓ NÚT YÊU CẦU EVIDENT + KÉO THẢ ĐẢO VỊ TRÍ) */}
                <ShiftChecklistEditor
                  items={currentShift.checklistItems}
                  onChange={(updatedItems) =>
                    setCurrentShift((prev) => ({
                      ...prev,
                      checklistItems: updatedItems,
                    }))
                  }
                />

                {/* Hộp dự toán cho ca này */}
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.35rem',
                    padding: '0.85rem 1rem',
                    backgroundColor: '#eff6ff',
                    borderRadius: '0.5rem',
                    marginBottom: '1rem',
                    border: '1px solid #bfdbfe',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.875rem', color: '#1e40af' }}>
                      Dự toán thù lao ca này: <strong>{currentDurationHours}h x {currentShift.requiredWorkers} người</strong>
                    </span>
                    <span style={{ fontSize: '1.15rem', fontWeight: 800, color: '#1d4ed8' }}>
                      {currentShiftTotal.toLocaleString()} đ
                    </span>
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                    * Tiền thù lao ca do Nhà tổ chức thanh toán trực tiếp cho nhân sự sau khi hoàn thành ca làm việc (không qua CANi).
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                  {editingShiftIndex !== null && (
                    <button
                      type="button"
                      className="eo-btn-secondary"
                      onClick={() => {
                        setEditingShiftIndex(null);
                        setIsAddingShift(false);
                      }}
                    >
                      Hủy sửa ca
                    </button>
                  )}
                  <button
                    type="submit"
                    disabled={submitting}
                    className="eo-btn-primary"
                    style={{ background: '#2563eb' }}
                  >
                    {submitting
                      ? 'Đang lưu...'
                      : editingShiftIndex !== null
                      ? `Lưu Cập Nhật Ca #${editingShiftIndex + 1}`
                      : 'Lưu Ca Làm Việc'}
                  </button>
                </div>
              </form>
            ) : null}

            {/* ĐIỀU HƯỚNG CUỐI BƯỚC 2 */}
            <div className="eo-modal-footer">
              <button type="button" className="eo-btn-secondary" onClick={() => setCurrentStep(1)}>
                Quay Lại
              </button>
              <button
                type="button"
                className="eo-btn-primary"
                disabled={shiftsList.length === 0}
                onClick={handleProceedToStep3}
                style={{
                  background: shiftsList.length > 0 ? '#2563eb' : '#94a3b8',
                  cursor: shiftsList.length > 0 ? 'pointer' : 'not-allowed',
                }}
              >
                Tiếp Tục
              </button>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* BƯỚC 3: XÁC NHẬN ĐỊA CHỈ CỤ THỂ & LƯU BẢN NHÁP / KÍCH HOẠT TÌM KIẾM NGAY */}
        {/* ======================================================== */}
        {currentStep === 3 && (
          <div className="animate-fade-in-up">
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
              Bước 3: Xác Nhận Thông Tin & Kích Hoạt Tìm Kiếm Nhân Sự
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 1.25rem' }}>
              Kiểm tra lại thông tin sự kiện, địa chỉ tổ chức cụ thể và các ca làm việc trước khi lưu bản nháp hoặc kích hoạt tìm kiếm nhân sự ngay.
            </p>

            {/* Hiển thị ảnh poster sự kiện trong 1 div riêng to rõ ở Bước 3 */}
            {(newEvent.imageUrl || createdEvent?.imageUrl) && (
              <div
                style={{
                  marginBottom: '1.25rem',
                  borderRadius: '0.75rem',
                  overflow: 'hidden',
                  border: '1px solid #cbd5e1',
                  backgroundColor: '#f8fafc',
                }}
              >
                <img
                  src={newEvent.imageUrl || createdEvent?.imageUrl}
                  alt={createdEvent?.name || newEvent.name}
                  style={{
                    width: '100%',
                    maxHeight: '240px',
                    objectFit: 'cover',
                    display: 'block',
                  }}
                  onError={(e) => {
                    e.target.style.display = 'none';
                  }}
                />
              </div>
            )}

            <div style={{ backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.25rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tên sự kiện:</span>
                <strong style={{ color: '#0f172a' }}>{createdEvent?.name || newEvent.name}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem', gap: '1rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem', whiteSpace: 'nowrap' }}>Địa chỉ tổ chức cụ thể:</span>
                <span style={{ fontWeight: 600, color: '#1e293b', textAlign: 'right' }}>
                  {newEvent.venueAddress || createdEvent?.venueAddress || 'Chưa cập nhật địa chỉ cụ thể'}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Thời gian tổ chức:</span>
                <span style={{ fontWeight: 600, color: '#1e293b' }}>
                  {formatDateTimeDisplay(createdEvent?.startTime || newEvent.startTime)} - {formatDateTimeDisplay(createdEvent?.endTime || newEvent.endTime)}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tổng số ca làm việc:</span>
                <strong>{shiftsList.length} ca</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tổng nhân sự cần tuyển:</span>
                <strong style={{ color: '#2563eb' }}>{totalWorkers} nhân sự</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '0.5rem', borderTop: '1px dashed #cbd5e1' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tổng dự toán thù lao ca:</span>
                <strong style={{ color: '#16a34a', fontSize: '1.05rem' }}>{totalEstimatedCost.toLocaleString()} đ</strong>
              </div>
              <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.35rem', textAlign: 'right' }}>
                (Thanh toán trực tiếp cho nhân sự sau khi xong ca)
              </div>
            </div>

            {/* Thông báo chính sách hạn mức gói dịch vụ */}
            <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '0.75rem', padding: '1.1rem', marginBottom: '1.5rem' }}>
              <div style={{ fontWeight: 700, color: '#15803d', marginBottom: '0.35rem' }}>
                Chính Sách Gói Dịch Vụ & Ưu Đãi Hội Viên
              </div>
              <p style={{ fontSize: '0.825rem', color: '#166534', margin: 0, lineHeight: 1.5 }}>
                Khi bấm <strong>Kích Hoạt Tìm Kiếm Ngay</strong>, hệ thống sẽ trừ <strong>1 sự kiện</strong> trong gói dịch vụ của bạn (hoặc áp dụng Gói Trải Nghiệm đã cấp sẵn khi tạo tài khoản). Nếu bấm <strong>Lưu Bản Nháp</strong>, sự kiện sẽ được lưu lại mà chưa trừ lượt gói cước.
              </p>
            </div>

            <div className="eo-modal-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="eo-btn-secondary"
                onClick={() => setCurrentStep(2)}
                disabled={submitting}
              >
                Quay Lại Sửa Ca
              </button>

              <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                <button
                  type="button"
                  onClick={handleSaveAsDraft}
                  disabled={submitting}
                  style={{
                    background: '#fffbeb',
                    color: '#b45309',
                    border: '1px solid #fcd34d',
                    borderRadius: '0.5rem',
                    padding: '0.75rem 1.35rem',
                    fontSize: '0.9rem',
                    fontWeight: 700,
                    cursor: submitting ? 'not-allowed' : 'pointer',
                    transition: 'all 0.2s',
                  }}
                  title="Lưu sự kiện ở trạng thái Bản nháp và mở trang Chi tiết sự kiện"
                >
                  Lưu Bản Nháp
                </button>

                <button
                  type="button"
                  className="eo-btn-primary"
                  disabled={submitting}
                  onClick={handleActivateMatching}
                  style={{
                    background: '#2563eb',
                    padding: '0.75rem 1.75rem',
                    fontSize: '0.95rem',
                    fontWeight: 700,
                    boxShadow: '0 4px 12px rgba(37, 99, 235, 0.3)',
                  }}
                >
                  {submitting ? 'Đang đăng tuyển...' : 'Đăng Tuyển & Tìm Người Ngay'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default EventCreationWizardModal;
