import React, { useMemo, useState } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { showConfirm } from '../../../components/common/PromptModal';
import {
  ROLES,
  WAGE_RECOMMENDATIONS_MAP,
  calculateShiftRiskAnalysis,
  normalizeChecklistItems,
  serializeChecklistItems,
} from '../hooks/useShifts';
import {
  isGibberishOrSpam,
  validateJobType,
  validateDuplicateShift,
  resolveWageRegionInfo,
  validateShiftWageAgainstFloor,
  detectCityFromAddress,
  STATUTORY_HOURLY_FLOORS,
} from '../utils/shiftValidation';
import {
  toDateTimeLocalInput,
  validateEventTimeRange,
  validateShiftTimeRange,
  validateShiftArrivalTime,
} from '../utils/eventHelpers';
import { getCoordinatesFromAddress } from '../utils/geoUtils';
import VenueLocationPicker from './VenueLocationPicker';
import ShiftChecklistEditor from './ShiftChecklistEditor';
import ShiftSkillSelector from './ShiftSkillSelector';
import ShiftArrivalTimeField from './ShiftArrivalTimeField';
import WageBenchmarkBox from './WageBenchmarkBox';
import './EventCreatePage.css';

/**
 * EventCreationWizardModal (hỗ trợ cả chế độ Trang Riêng `asPage={true}` và Modal)
 * Quy trình 3 bước:
 * 1. Khởi tạo Sự kiện & Địa điểm
 * 2. Thiết lập Danh sách Ca làm việc (Shifts), Validate vị trí, Gợi ý lương có dẫn chứng & Checklist
 * 3. Xác nhận thông tin, dự toán chi phí & Lưu Bản Nháp / Kích Hoạt Tuyển Dụng
 */
const EventCreationWizardModal = ({
  isOpen = true,
  asPage = false,
  controlledStep = null,
  onStepChange,
  onClose,
  onSuccess,
  onEventCreated,
  newEvent,
  setNewEvent,
  skillTaxonomy = [],
}) => {
  const skillNameMap = useMemo(() => {
    const map = {};
    (Array.isArray(skillTaxonomy) ? skillTaxonomy : []).forEach((sk) => {
      if (sk && sk.skillId) map[sk.skillId] = sk.name;
    });
    return map;
  }, [skillTaxonomy]);

  const [internalStep, setInternalStep] = useState(1);
  const currentStep = controlledStep != null ? controlledStep : internalStep;
  const setCurrentStep = (nextStep) => {
    setInternalStep(nextStep);
    onStepChange?.(nextStep);
  };
  const [submitting, setSubmitting] = useState(false);
  const [createdEvent, setCreatedEvent] = useState(null);
  const [step1Errors, setStep1Errors] = useState({});
  const [shiftErrors, setShiftErrors] = useState({});

  const [uploadingEventImg, setUploadingEventImg] = useState(false);
  const [uploadingShiftImg, setUploadingShiftImg] = useState(false);
  const eventFileInputRef = React.useRef(null);
  const shiftFileInputRef = React.useRef(null);

  const [shiftsList, setShiftsList] = useState([]);
  const [isAddingShift, setIsAddingShift] = useState(true);
  const [editingShiftIndex, setEditingShiftIndex] = useState(null);
  const [wageMode, setWageMode] = useState('HOURLY'); // 'HOURLY' | 'PER_SHIFT'
  const [shiftPackageWage, setShiftPackageWage] = useState(200000);
  const [currentShiftBenchmark, setCurrentShiftBenchmark] = useState(null);

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

  const [currentShift, setCurrentShift] = useState(() => ({
    jobType: ROLES[0],
    description: 'Hỗ trợ công tác đón tiếp khách, hướng dẫn vị trí và điều phối sự kiện theo phân công.',
    startTime: newEvent.startTime || '',
    endTime: newEvent.endTime || '',
    arrivalTime: '',
    requiredWorkers: 1,
    hourlyWage: 50000,
    allowance: 0,
    maxDistanceKm: 15,
    imageUrl: '',
    performedSkillId: null,
    requiredSkills: [],
    skillRequirements: [],
    customSkillNotes: '',
    checklistItems: [
      { id: 'chk_1', title: 'Có mặt trước 20 phút nhận thẻ và vị trí', isRequiredEvidence: false },
      { id: 'chk_2', title: 'Đồng phục chỉn chu theo quy định', isRequiredEvidence: true },
      { id: 'chk_3', title: 'Quét mã QR Check-in đầu ca', isRequiredEvidence: false },
      { id: 'chk_4', title: 'Báo cáo quản lý trước khi kết thúc ca', isRequiredEvidence: false },
    ],
  }));

  if (!isOpen) return null;

  const activeAddress = newEvent.venueAddress || createdEvent?.venueAddress || '';
  const activeLat = newEvent.venueLat ?? createdEvent?.venueLat ?? null;
  const activeLng = newEvent.venueLng ?? createdEvent?.venueLng ?? null;
  const regionInfo = resolveWageRegionInfo(
    activeAddress,
    activeLat,
    activeLng,
    Boolean(newEvent.locationPicked || createdEvent?.eventId)
  );
  const floorInfo = regionInfo.isResolved
    ? { wage: regionInfo.wage, regionLabel: regionInfo.regionLabel }
    : { wage: 25500, regionLabel: 'Chưa xác định khu vực' };

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

  const handleStartEditShift = (shift, index) => {
    const sTime = toDateTimeInputVal(shift.startTime) || newEvent.startTime || '';
    const eTime = toDateTimeInputVal(shift.endTime) || newEvent.endTime || '';
    const dur = getDurationHours(sTime, eTime);
    const hWage = Number(shift.hourlyWage || 50000);
    const parsedChecklist = normalizeChecklistItems(shift.checklistItems || shift.checklist);
    const customArrival =
      shift.arrivalTime && shift.startTime &&
      new Date(shift.arrivalTime).getTime() !== new Date(shift.startTime).getTime()
        ? toDateTimeInputVal(shift.arrivalTime)
        : '';

    setEditingShiftIndex(index);
    setShiftErrors({});
    setWageMode('HOURLY');
    setShiftPackageWage(Math.round(hWage * dur));
    setCurrentShift({
      jobType: shift.jobType || '',
      description: shift.description || '',
      startTime: sTime,
      endTime: eTime,
      arrivalTime: customArrival,
      requiredWorkers: Number(shift.requiredWorkers || 2),
      hourlyWage: hWage,
      allowance: Number(shift.allowance || 0),
      maxDistanceKm: Number(shift.maxDistanceKm || 15),
      imageUrl: shift.imageUrl || '',
      performedSkillId: shift.performedSkillId || null,
      requiredSkills: Array.isArray(shift.requiredSkills) ? shift.requiredSkills : [],
      skillRequirements: Array.isArray(shift.skillRequirements) ? shift.skillRequirements : [],
      customSkillNotes: shift.customSkillNotes || '',
      checklistItems: parsedChecklist,
    });
    setIsAddingShift(true);
  };

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
      const safeWage = Number(currentShift.hourlyWage || 0) > 0 ? Number(currentShift.hourlyWage) : 50000;
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
          performedSkillId: null,
          requiredSkills: [],
          skillRequirements: [],
          customSkillNotes: '',
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

  const currentDurationHours = getDurationHours(currentShift.startTime, currentShift.endTime);
  const currentShiftTotal =
    currentDurationHours *
      Math.max(1, Number(currentShift.requiredWorkers || 1)) *
      Math.max(0, Number(currentShift.hourlyWage || 0)) +
    Math.max(0, Number(currentShift.allowance || 0));

  const riskAnalysis = calculateShiftRiskAnalysis(currentShift, activeAddress, activeLat, activeLng);

  const handleRoleChange = (role) => {
    const rec = WAGE_RECOMMENDATIONS_MAP[role];
    const nextHourly = rec ? rec.defaultWage : currentShift.hourlyWage;
    setCurrentShift((prev) => ({
      ...prev,
      jobType: role,
      hourlyWage: nextHourly,
    }));
    if (shiftErrors.jobType) {
      setShiftErrors((prev) => ({ ...prev, jobType: null }));
    }
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
    let raw = typeof val === 'string' ? val.replace(/^0+(?=\d)/, '') : String(val ?? '');
    const pkg = raw === '' ? '' : Math.max(0, parseFloat(raw) || 0);
    setShiftPackageWage(pkg);
    const computedHourly = Math.max(0, Math.round((Number(pkg) || 0) / Math.max(0.5, currentDurationHours)));
    setCurrentShift((prev) => ({
      ...prev,
      hourlyWage: computedHourly,
    }));
  };

  const handleShiftTimeChange = (field, value) => {
    const nextStart = field === 'startTime' ? value : currentShift.startTime;
    const nextEnd = field === 'endTime' ? value : currentShift.endTime;
    const nextDur = getDurationHours(nextStart, nextEnd);

    let nextArrival = currentShift.arrivalTime;
    if (field === 'startTime' && currentShift.arrivalTime && currentShift.startTime) {
      const oldS = new Date(currentShift.startTime);
      const oldA = new Date(currentShift.arrivalTime);
      const newS = new Date(value);
      if (!isNaN(oldS.getTime()) && !isNaN(oldA.getTime()) && !isNaN(newS.getTime())) {
        const offsetMs = oldS.getTime() - oldA.getTime();
        if (offsetMs >= 0 && offsetMs <= 120 * 60 * 1000) {
          nextArrival = toDateTimeLocalInput(new Date(newS.getTime() - offsetMs));
        }
      }
    }

    if (wageMode === 'PER_SHIFT') {
      const computedHourly = Math.max(1, Math.round(Number(shiftPackageWage || 0) / Math.max(0.5, nextDur)));
      setCurrentShift((prev) => ({
        ...prev,
        [field]: value,
        arrivalTime: nextArrival,
        hourlyWage: computedHourly,
      }));
    } else {
      setCurrentShift((prev) => ({
        ...prev,
        [field]: value,
        arrivalTime: nextArrival,
      }));
      setShiftPackageWage(Math.round(Number(currentShift.hourlyWage || 0) * nextDur));
    }
  };

  const validateStep1 = (data) => {
    const errs = {};
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

    const desc = data.description?.trim() || '';
    if (desc) {
      if (desc.length < 3) {
        errs.description = 'Mô tả sự kiện quá ngắn (tối thiểu 3 ký tự nếu nhập).';
      } else if (isGibberishOrSpam(desc)) {
        errs.description = 'Mô tả sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.';
      }
    }

    const timeErrs = validateEventTimeRange(data.startTime, data.endTime, { allowPastStart: false });
    Object.assign(errs, timeErrs);

    if (!data.venueAddress?.trim()) {
      errs.venueAddress = 'Vui lòng chọn hoặc nhập địa chỉ tổ chức cụ thể.';
    }

    return errs;
  };

  const handleStep1Submit = async (e) => {
    e.preventDefault();
    const errs = validateStep1(newEvent);
    if (Object.keys(errs).length > 0) {
      setStep1Errors(errs);
      showToast(Object.values(errs)[0], 'error');
      return;
    }
    setStep1Errors({});

    const evtStart = new Date(newEvent.startTime);
    const evtEnd = new Date(newEvent.endTime);

    setSubmitting(true);
    try {
      const { venueCoordinatesConfirmed, ...eventDetails } = newEvent;
      const eventPayload = {
        ...eventDetails,
        startTime: evtStart.toISOString(),
        endTime: evtEnd.toISOString(),
        eventDate: evtStart.toISOString().substring(0, 10),
      };
      if (!venueCoordinatesConfirmed && eventPayload.venueAddress?.trim()) {
        try {
          const coords = await getCoordinatesFromAddress(eventPayload.venueAddress);
          if (coords && coords.lat && coords.lng) {
            eventPayload.venueLat = coords.lat;
            eventPayload.venueLng = coords.lng;
            setNewEvent((prev) => ({ ...prev, venueLat: coords.lat, venueLng: coords.lng }));
          }
        } catch {
          // Sử dụng fallback an toàn khi geocoding lỗi mạng
        }
      }
      eventPayload.venueLat ??= 10.7328;
      eventPayload.venueLng ??= 106.7214;

      let evt = createdEvent;
      if (!evt) {
        evt = await eoService.createEvent(eventPayload);
        setCreatedEvent(evt);
        showToast('Tạo sự kiện thành công! Hãy phân chia các ca làm việc trong sự kiện.', 'success');
      } else {
        evt = await eoService.updateEvent(evt.eventId, eventPayload);
        setCreatedEvent(evt);
        showToast('Đã cập nhật thông tin sự kiện.', 'success');
      }

      if (shiftsList.length === 0) {
        const defaultShiftStart = toDateTimeInputVal(evtStart);
        const defaultShiftEndMs = Math.min(evtStart.getTime() + 4 * 3600 * 1000, evtEnd.getTime());
        const defaultShiftEnd = toDateTimeInputVal(new Date(defaultShiftEndMs));
        setCurrentShift((prev) => ({
          ...prev,
          startTime: defaultShiftStart,
          endTime: defaultShiftEnd,
          arrivalTime: '',
        }));
      }

      setCurrentStep(2);
      setIsAddingShift(shiftsList.length === 0);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi lưu sự kiện', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveShift = async (e) => {
    e?.preventDefault();
    if (!createdEvent?.eventId) {
      showToast('Không tìm thấy sự kiện. Vui lòng hoàn thành Bước 1 trước.', 'error');
      return;
    }

    const nextShiftErrs = {};
    const jobTypeErr = validateJobType(currentShift.jobType);
    if (jobTypeErr) {
      nextShiftErrs.jobType = jobTypeErr;
    } else {
      const dupErr = validateDuplicateShift(currentShift, shiftsList, {
        excludeIndex: editingShiftIndex,
      });
      if (dupErr) nextShiftErrs.jobType = dupErr;
    }

    const descTrimmed = currentShift.description?.trim() || '';
    if (!descTrimmed) {
      nextShiftErrs.description = 'Vui lòng nhập mô tả nhiệm vụ công việc cho ca làm.';
    } else if (descTrimmed.length < 6) {
      nextShiftErrs.description = 'Mô tả công việc quá ngắn (tối thiểu 6 ký tự).';
    } else if (isGibberishOrSpam(descTrimmed)) {
      nextShiftErrs.description = 'Mô tả công việc không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.';
    }

    if (Object.keys(nextShiftErrs).length > 0) {
      setShiftErrors(nextShiftErrs);
      showToast(Object.values(nextShiftErrs)[0], 'error');
      return;
    }
    setShiftErrors({});

    if (Number(currentShift.allowance || 0) < 0) {
      showToast('Phụ cấp thêm không được là số âm!', 'error');
      return;
    }

    const wageCheck = validateShiftWageAgainstFloor({
      hourlyWage: currentShift.hourlyWage,
      benchmark: currentShiftBenchmark,
      city: regionInfo.city,
      venueAddress: activeAddress,
      venueLat: activeLat,
      venueLng: activeLng,
    });
    if (!wageCheck.isValid) {
      setShiftErrors((prev) => ({ ...prev, hourlyWage: wageCheck.error }));
      showToast(wageCheck.error, 'error');
      return;
    }

    if (Number(currentShift.requiredWorkers || 0) < 1) {
      showToast('Số lượng nhân sự cần tuyển tối thiểu là 1 người!', 'error');
      return;
    }

    const sStart = new Date(currentShift.startTime);
    const sEnd = new Date(currentShift.endTime);
    const eStart = new Date(createdEvent?.startTime || newEvent.startTime);
    const eEnd = new Date(createdEvent?.endTime || newEvent.endTime);

    const shiftTimeErr = validateShiftTimeRange(
      currentShift.startTime,
      currentShift.endTime,
      createdEvent?.startTime || newEvent.startTime,
      createdEvent?.endTime || newEvent.endTime,
      { allowPastStart: false }
    );
    if (shiftTimeErr) {
      showToast(shiftTimeErr, 'error');
      return;
    }
    const arrivalErr = validateShiftArrivalTime(currentShift.arrivalTime, currentShift.startTime);
    if (arrivalErr) {
      showToast(arrivalErr, 'error');
      return;
    }

    setSubmitting(true);
    try {
      const normalizedChecklist = normalizeChecklistItems(currentShift.checklistItems);
      const checklistJson = serializeChecklistItems(normalizedChecklist);
      const payload = {
        ...currentShift,
        jobType: currentShift.jobType.trim(),
        description: descTrimmed,
        requiredWorkers: Math.max(1, Number(currentShift.requiredWorkers || 1)),
        hourlyWage: Number(currentShift.hourlyWage || 0),
        allowance: Math.max(0, Number(currentShift.allowance || 0)),
        imageUrl: currentShift.imageUrl?.trim() || null,
        performedSkillId: currentShift.performedSkillId || null,
        requiredSkills: Array.isArray(currentShift.requiredSkills) ? currentShift.requiredSkills : [],
        skillRequirements: Array.isArray(currentShift.skillRequirements) ? currentShift.skillRequirements : [],
        customSkillNotes: currentShift.customSkillNotes?.trim() || null,
        checklist: checklistJson,
        arrivalTime: currentShift.arrivalTime ? new Date(currentShift.arrivalTime).toISOString() : null,
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

      const nextRoleIndex = (shiftsList.length + 1) % ROLES.length;
      const nextRole = ROLES[nextRoleIndex];
      const nextRec = WAGE_RECOMMENDATIONS_MAP[nextRole];

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
      setShiftErrors({});

      setCurrentShift({
        jobType: nextRole,
        description: 'Phối hợp thực hiện nhiệm vụ theo kế hoạch tổ chức sự kiện.',
        startTime: toDateTimeInputVal(nextStart),
        endTime: toDateTimeInputVal(nextEnd),
        arrivalTime: '',
        requiredWorkers: 1,
        hourlyWage: nextWage,
        allowance: 0,
        maxDistanceKm: 15,
        imageUrl: '',
        performedSkillId: null,
        requiredSkills: [],
        skillRequirements: [],
        customSkillNotes: '',
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

  const handleDeleteShift = async (shiftId, index) => {
    const confirmed = await showConfirm({
      title: 'Xóa ca làm việc',
      description: 'Bạn có chắc chắn muốn xóa ca làm việc này khỏi sự kiện?',
      type: 'danger',
      confirmText: 'Xóa ca',
    });
    if (!confirmed) return;
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

  const handleProceedToStep3 = () => {
    if (shiftsList.length === 0) {
      showToast('Vui lòng thêm ít nhất một ca làm việc trước khi tiếp tục!', 'warning');
      return;
    }

    const invalidShift = shiftsList.find((s) => {
      const check = validateShiftWageAgainstFloor({
        hourlyWage: s.hourlyWage,
        city: regionInfo.city,
        venueAddress: activeAddress,
        venueLat: activeLat,
        venueLng: activeLng,
      });
      return !check.isValid;
    });
    if (invalidShift) {
      const check = validateShiftWageAgainstFloor({
        hourlyWage: invalidShift.hourlyWage,
        city: regionInfo.city,
        venueAddress: activeAddress,
        venueLat: activeLat,
        venueLng: activeLng,
      });
      showToast(
        `Ca "${invalidShift.jobType}": ${check.error} Vui lòng điều chỉnh trước khi tiếp tục.`,
        'error'
      );
      return;
    }

    setCurrentStep(3);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

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
      const refreshed = await onSuccess?.();
      handleClose();
      onEventCreated?.(finalEvent, refreshed);
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi kích hoạt tìm kiếm', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveAsDraft = async () => {
    const finalEvent = buildFinalEventObject('DRAFT');
    showToast('Đã lưu sự kiện ở chế độ Bản nháp! Chuyển sang trang chi tiết sự kiện.', 'success');
    const refreshed = await onSuccess?.();
    handleClose();
    onEventCreated?.(finalEvent, refreshed);
  };

  const handleClose = () => {
    setInternalStep(1);
    setCreatedEvent(null);
    setShiftsList([]);
    setIsAddingShift(true);
    setStep1Errors({});
    setShiftErrors({});
    onClose?.();
  };

  const handleRequestCancelWizard = async () => {
    if (createdEvent?.eventId && shiftsList.length === 0) {
      const discardEmpty = await showConfirm({
        title: 'Sự kiện chưa có ca làm việc',
        description: `Sự kiện "${createdEvent.name}" vừa lưu nháp ở Bước 1 nhưng chưa có ca làm việc nào. Bạn muốn xóa bản nháp trống này hay giữ lại trong danh sách?`,
        type: 'warning',
        confirmText: 'Xóa bản nháp trống',
        cancelText: 'Giữ lại bản nháp',
      });
      if (discardEmpty) {
        try {
          await eoService.deleteEvent(createdEvent.eventId);
          await onSuccess?.();
          showToast('Đã xóa bản nháp sự kiện chưa có ca làm việc.', 'info');
        } catch {
          // Bỏ qua nếu không xóa được
        }
      } else {
        await onSuccess?.();
      }
    }
    handleClose();
  };

  const canJumpToStep = (targetStep) => {
    if (targetStep === 1) return true;
    if (targetStep === 2) return Boolean(createdEvent?.eventId);
    if (targetStep === 3) return Boolean(createdEvent?.eventId) && shiftsList.length > 0;
    return false;
  };

  const stepItems = [
    { num: 1, title: 'Sự kiện & Địa điểm', sub: 'Tên, thời gian, bản đồ' },
    { num: 2, title: 'Danh sách ca làm', sub: 'Vị trí, thù lao, đầu việc' },
    { num: 3, title: 'Xác nhận & Đăng tuyển', sub: 'Dự toán & kích hoạt' },
  ];

  const wizardMainContent = (
    <>
      {/* BƯỚC 1: THÔNG TIN SỰ KIỆN */}
      {currentStep === 1 && (
        <form onSubmit={handleStep1Submit} className="animate-fade-in-up">
          <h3 style={{ margin: '0 0 0.4rem', fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>
            Bước 1: Khởi Tạo Sự Kiện & Địa Điểm
          </h3>
          <p style={{ fontSize: '0.86rem', color: '#64748b', margin: '0 0 1.25rem' }}>
            Nhập tên sự kiện, khung giờ tổ chức và ghim chính xác địa điểm để CANi đối chiếu sàn lương vùng và bán kính tuyển dụng.
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
                  setStep1Errors((prev) => ({
                    ...prev,
                    name: 'Tên sự kiện không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa (vui lòng nhập tên có nghĩa).',
                  }));
                }
              }}
            />
            {step1Errors.name && <div className="ecw-field-error">{step1Errors.name}</div>}
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
              placeholder="Mô tả quy mô (ví dụ: hội nghị 300 khách, khu vực sảnh A, yêu cầu tác phong...)"
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
            {step1Errors.description && <div className="ecw-field-error">{step1Errors.description}</div>}
          </div>

          <div className="eo-form-row">
            <div className="eo-form-group">
              <label>Thời Gian Bắt Đầu Sự Kiện *</label>
              <input
                type="datetime-local"
                min={toDateTimeLocalInput(new Date())}
                required
                className="eo-form-input"
                style={{
                  borderColor: step1Errors.startTime || step1Errors.time ? '#ef4444' : undefined,
                }}
                value={newEvent.startTime || ''}
                onChange={(e) => {
                  const nextStart = e.target.value;
                  const nextData = { ...newEvent, startTime: nextStart };
                  setNewEvent(nextData);
                  const tErrs = validateEventTimeRange(nextStart, nextData.endTime, { allowPastStart: false });
                  setStep1Errors((prev) => ({
                    ...prev,
                    startTime: tErrs.startTime || null,
                    endTime: tErrs.endTime || null,
                    time: tErrs.time || null,
                  }));
                }}
              />
            </div>
            <div className="eo-form-group">
              <label>Thời Gian Kết Thúc Sự Kiện *</label>
              <input
                type="datetime-local"
                min={newEvent.startTime || toDateTimeLocalInput(new Date())}
                required
                className="eo-form-input"
                style={{
                  borderColor: step1Errors.endTime || step1Errors.time ? '#ef4444' : undefined,
                }}
                value={newEvent.endTime || ''}
                onChange={(e) => {
                  const nextEnd = e.target.value;
                  const nextData = { ...newEvent, endTime: nextEnd };
                  setNewEvent(nextData);
                  const tErrs = validateEventTimeRange(nextData.startTime, nextEnd, { allowPastStart: false });
                  setStep1Errors((prev) => ({
                    ...prev,
                    startTime: tErrs.startTime || null,
                    endTime: tErrs.endTime || null,
                    time: tErrs.time || null,
                  }));
                }}
              />
            </div>
          </div>
          {(step1Errors.time || step1Errors.startTime || step1Errors.endTime) && (
            <div className="ecw-field-error" style={{ marginTop: '-0.5rem', marginBottom: '0.75rem' }}>
              {step1Errors.time || step1Errors.startTime || step1Errors.endTime}
            </div>
          )}

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
                }}
              >
                <img
                  src={newEvent.imageUrl}
                  alt="Event Poster Preview"
                  style={{ width: '100%', maxHeight: '240px', objectFit: 'cover', display: 'block' }}
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
                    style={{ background: 'none', border: 'none', color: '#dc2626', fontSize: '0.82rem', cursor: 'pointer', fontWeight: 700 }}
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
            coordinatesConfirmed={newEvent.venueCoordinatesConfirmed}
            onLocationChange={({ address, lat, lng, locationPicked, venueCoordinatesConfirmed }) => {
              setNewEvent((prev) => ({
                ...prev,
                venueAddress: address,
                venueLat: lat,
                venueLng: lng,
                locationPicked,
                venueCoordinatesConfirmed,
              }));
              if (step1Errors.venueAddress) {
                setStep1Errors((prev) => ({ ...prev, venueAddress: null }));
              }
            }}
            required
          />
          {step1Errors.venueAddress && <div className="ecw-field-error">{step1Errors.venueAddress}</div>}

          <div className="eo-modal-footer">
            <button type="button" className="eo-btn-secondary" onClick={handleRequestCancelWizard}>
              Hủy Bỏ
            </button>
            <button type="submit" disabled={submitting} className="eo-btn-primary">
              {submitting ? 'Đang lưu...' : createdEvent ? 'Lưu Thay Đổi & Tiếp Tục' : 'Tiếp Tục Sang Bước 2'}
            </button>
          </div>
        </form>
      )}

      {/* BƯỚC 2: TẠO & CHỈNH SỬA CA LÀM VIỆC */}
      {currentStep === 2 && (
        <div className="animate-fade-in-up">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.85rem', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>
                Bước 2: Thiết Lập Danh Sách Ca Làm Việc
              </h3>
              <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0.25rem 0 0' }}>
                Sự kiện: <strong>{createdEvent?.name || newEvent.name}</strong>. Các ca làm việc phải nằm trong khung giờ sự kiện.
              </p>
            </div>

            {!isAddingShift && (
              <button
                type="button"
                className="eo-btn-primary"
                style={{ fontSize: '0.85rem', padding: '0.55rem 1.05rem' }}
                onClick={() => {
                  setEditingShiftIndex(null);
                  setShiftErrors({});
                  setIsAddingShift(true);
                }}
              >
                + Thêm Ca Mới
              </button>
            )}
          </div>

          <div
            style={{
              backgroundColor: '#eff6ff',
              border: '1px solid #bfdbfe',
              borderRadius: '0.75rem',
              padding: '0.9rem 1rem',
              marginBottom: '1.25rem',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '0.75rem',
            }}
          >
            <div>
              <div style={{ fontSize: '0.82rem', fontWeight: 700, color: '#1e40af', marginBottom: '0.15rem' }}>
                Khung giờ sự kiện ({floorInfo.regionLabel}):
              </div>
              <div style={{ fontSize: '0.88rem', color: '#0f172a', fontWeight: 600 }}>
                {formatDateTimeDisplay(createdEvent?.startTime || newEvent.startTime)} đến{' '}
                {formatDateTimeDisplay(createdEvent?.endTime || newEvent.endTime)}
                <span style={{ color: '#2563eb', marginLeft: '0.4rem', fontWeight: 700 }}>
                  ({getDurationHours(createdEvent?.startTime || newEvent.startTime, createdEvent?.endTime || newEvent.endTime)} giờ)
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.75rem', color: '#475569', fontWeight: 600 }}>Chia nhanh thời gian:</span>
              {[2, 3].map((cnt) => (
                <button
                  key={cnt}
                  type="button"
                  disabled={submitting}
                  onClick={() => handleQuickSplit(cnt)}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#2563eb',
                    border: '1px solid #93c5fd',
                    borderRadius: '0.45rem',
                    padding: '0.4rem 0.75rem',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    cursor: submitting ? 'not-allowed' : 'pointer',
                  }}
                >
                  Chia {cnt} Ca
                </button>
              ))}
            </div>
          </div>

          {shiftsList.length > 0 && (
            <div style={{ marginBottom: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div style={{ fontSize: '0.875rem', fontWeight: 700, color: '#334155', display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
                <span>Danh sách ca đã thêm ({shiftsList.length} ca):</span>
                <span style={{ color: '#2563eb' }}>
                  Tổng: {totalWorkers} nhân sự • Dự toán: {totalEstimatedCost.toLocaleString('vi-VN')} đ
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
                      borderRadius: '0.75rem',
                      padding: '1rem',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.75rem',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
                      <div style={{ flex: 1, minWidth: '220px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.35rem', flexWrap: 'wrap' }}>
                          <span style={{ backgroundColor: '#eff6ff', color: '#1d4ed8', fontWeight: 700, fontSize: '0.82rem', padding: '0.2rem 0.55rem', borderRadius: '0.35rem' }}>
                            Ca #{idx + 1}
                          </span>
                          <strong style={{ fontSize: '1rem', color: '#0f172a' }}>{shift.jobType}</strong>
                          <span style={{ fontSize: '0.85rem', color: '#16a34a', fontWeight: 600 }}>
                            {shift.requiredWorkers} người x {Number(shift.hourlyWage || 0).toLocaleString('vi-VN')} đ/h
                          </span>
                          <span
                            style={{
                              fontSize: '0.74rem',
                              padding: '0.15rem 0.5rem',
                              borderRadius: '999px',
                              backgroundColor: parsedTasks.length > 0 ? '#f0fdf4' : '#fffbeb',
                              color: parsedTasks.length > 0 ? '#15803d' : '#b45309',
                              border: parsedTasks.length > 0 ? '1px solid #bbf7d0' : '1px solid #fde68a',
                              fontWeight: 600,
                            }}
                          >
                            {parsedTasks.length > 0 ? `${parsedTasks.length} đầu việc` : 'Chưa có đầu việc'}
                          </span>
                        </div>

                        <div style={{ fontSize: '0.82rem', color: '#64748b', display: 'flex', flexWrap: 'wrap', gap: '0.85rem' }}>
                          <span>
                            Thời gian: {sHours}h (
                            {new Date(shift.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                            {new Date(shift.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})
                          </span>
                          {shift.arrivalTime &&
                            new Date(shift.arrivalTime).getTime() !== new Date(shift.startTime).getTime() && (
                              <span style={{ color: '#b45309', fontWeight: 600 }}>
                                Có mặt từ {new Date(shift.arrivalTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                              </span>
                            )}
                          <span>
                            Dự toán ca: <strong>{computeShiftCost(shift).toLocaleString('vi-VN')} đ</strong>
                          </span>
                          {shift.allowance > 0 && <span>Phụ cấp: {Number(shift.allowance).toLocaleString('vi-VN')} đ</span>}
                        </div>

                        {((shift.performedSkillId || shift.performedSkillName) ||
                          (Array.isArray(shift.skillRequirements) && shift.skillRequirements.length > 0) ||
                          (Array.isArray(shift.requiredSkills) && shift.requiredSkills.length > 0)) && (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginTop: '0.4rem' }}>
                            {(shift.performedSkillName || skillNameMap[shift.performedSkillId]) && (
                              <span
                                style={{
                                  fontSize: '0.72rem',
                                  padding: '0.15rem 0.5rem',
                                  borderRadius: '999px',
                                  backgroundColor: '#ecfdf5',
                                  color: '#047857',
                                  border: '1px solid #a7f3d0',
                                  fontWeight: 700,
                                }}
                              >
                                Kỹ năng chính: {shift.performedSkillName || skillNameMap[shift.performedSkillId]}
                              </span>
                            )}
                            {Array.isArray(shift.skillRequirements) && shift.skillRequirements.length > 0
                              ? shift.skillRequirements.map((req) => (
                                  <span
                                    key={req.skillId}
                                    style={{
                                      fontSize: '0.72rem',
                                      padding: '0.15rem 0.5rem',
                                      borderRadius: '999px',
                                      backgroundColor: req.requirementType === 'PREFERRED' ? '#f8fafc' : '#eff6ff',
                                      color: req.requirementType === 'PREFERRED' ? '#475569' : '#1d4ed8',
                                      border: req.requirementType === 'PREFERRED' ? '1px solid #cbd5e1' : '1px solid #bfdbfe',
                                      fontWeight: 600,
                                    }}
                                  >
                                    {req.requirementType === 'PREFERRED' ? 'Ưu tiên' : 'Bắt buộc'}:{' '}
                                    {req.skillName || skillNameMap[req.skillId] || 'Kỹ năng'}
                                  </span>
                                ))
                              : (shift.requiredSkills || []).map((skillId) => (
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
                                    Bắt buộc: {skillNameMap[skillId] || 'Kỹ năng yêu cầu'}
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
                            borderRadius: '0.45rem',
                            padding: '0.45rem 0.85rem',
                            fontSize: '0.8rem',
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
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
                            borderRadius: '0.45rem',
                            padding: '0.45rem 0.75rem',
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          Xóa Ca
                        </button>
                      </div>
                    </div>

                    {shift.imageUrl && (
                      <div style={{ borderRadius: '0.5rem', overflow: 'hidden', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
                        <img
                          src={shift.imageUrl}
                          alt={shift.jobType}
                          style={{ width: '100%', maxHeight: '180px', objectFit: 'cover', display: 'block' }}
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

          {isAddingShift && (
            <form
              onSubmit={handleSaveShift}
              style={{
                backgroundColor: '#f8fafc',
                padding: '1.35rem',
                borderRadius: '0.85rem',
                border: editingShiftIndex !== null ? '2px solid #2563eb' : '1.5px solid #cbd5e1',
                marginBottom: '1.5rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.9rem' }}>
                <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700, color: '#1e40af' }}>
                  {editingShiftIndex !== null
                    ? `Đang Chỉnh Sửa Ca #${editingShiftIndex + 1}`
                    : `Thông Tin Ca Làm Việc ${shiftsList.length > 0 ? `Thứ ${shiftsList.length + 1}` : 'Đầu Tiên'}`}
                </h4>
                {shiftsList.length > 0 && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditingShiftIndex(null);
                      setShiftErrors({});
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
                  style={{
                    borderColor: shiftErrors.jobType ? '#ef4444' : undefined,
                    boxShadow: shiftErrors.jobType ? '0 0 0 1px #ef4444' : undefined,
                  }}
                  placeholder="Chọn gợi ý hoặc tự nhập chức danh hợp lệ (VD: MC song ngữ, Barista, Kỹ thuật LED...)"
                  value={currentShift.jobType}
                  onChange={(e) => handleRoleChange(e.target.value)}
                  onBlur={() => {
                    const err = validateJobType(currentShift.jobType);
                    setShiftErrors((prev) => ({ ...prev, jobType: err }));
                  }}
                  required
                />
                <datalist id="wizard-roles-list">
                  {ROLES.map((role) => (
                    <option key={role} value={role} />
                  ))}
                </datalist>
                {shiftErrors.jobType && <div className="ecw-field-error">{shiftErrors.jobType}</div>}

                <div className="ecw-role-chips" aria-label="Chọn nhanh vị trí chuẩn">
                  {ROLES.map((role) => (
                    <button
                      key={role}
                      type="button"
                      className={`ecw-role-chip ${currentShift.jobType === role ? 'is-active' : ''}`}
                      onClick={() => handleRoleChange(role)}
                    >
                      {role}
                    </button>
                  ))}
                </div>
              </div>

              <div className="eo-form-group">
                <label>Mô Tả Nhiệm Vụ Công Việc Trong Ca *</label>
                <textarea
                  rows="2"
                  required
                  className="eo-form-textarea"
                  style={{
                    borderColor: shiftErrors.description ? '#ef4444' : undefined,
                    boxShadow: shiftErrors.description ? '0 0 0 1px #ef4444' : undefined,
                  }}
                  placeholder="VD: Hỗ trợ đón tiếp khách, hướng dẫn vị trí, điều phối micro..."
                  value={currentShift.description}
                  onChange={(e) => {
                    setCurrentShift({ ...currentShift, description: e.target.value });
                    if (shiftErrors.description) setShiftErrors((prev) => ({ ...prev, description: null }));
                  }}
                  onBlur={() => {
                    const desc = currentShift.description?.trim() || '';
                    let err = null;
                    if (!desc) err = 'Vui lòng nhập mô tả nhiệm vụ công việc cho ca làm.';
                    else if (desc.length < 6) err = 'Mô tả công việc quá ngắn (tối thiểu 6 ký tự).';
                    else if (isGibberishOrSpam(desc)) err = 'Mô tả công việc không hợp lệ hoặc chứa chuỗi ký tự vô nghĩa.';
                    setShiftErrors((prev) => ({ ...prev, description: err }));
                  }}
                />
                {shiftErrors.description && <div className="ecw-field-error">{shiftErrors.description}</div>}
              </div>

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
                    style={{ fontSize: '0.825rem', padding: '0.5rem 1rem', fontWeight: 600, whiteSpace: 'nowrap' }}
                  >
                    {uploadingShiftImg ? 'Đang tải ảnh lên...' : 'Tải ảnh từ máy tính'}
                  </button>
                  {!currentShift.imageUrl && (
                    <span style={{ fontSize: '0.78rem', color: '#64748b' }}>Chưa chọn ảnh minh họa cho ca này</span>
                  )}
                </div>

                {currentShift.imageUrl && (
                  <div style={{ marginTop: '0.5rem', borderRadius: '0.625rem', border: '1px solid #cbd5e1', overflow: 'hidden', backgroundColor: '#ffffff' }}>
                    <img
                      src={currentShift.imageUrl}
                      alt="Shift Preview"
                      style={{ width: '100%', maxHeight: '220px', objectFit: 'cover', display: 'block' }}
                      onError={(e) => {
                        e.target.style.display = 'none';
                      }}
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.45rem 0.85rem', borderTop: '1px solid #e2e8f0' }}>
                      <span style={{ fontSize: '0.8rem', color: '#15803d', fontWeight: 600 }}>Đã tải ảnh minh họa ca làm việc</span>
                      <button
                        type="button"
                        onClick={() => setCurrentShift({ ...currentShift, imageUrl: '' })}
                        style={{ background: 'none', border: 'none', color: '#dc2626', fontSize: '0.8rem', cursor: 'pointer', fontWeight: 700 }}
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
                    min={toDateTimeLocalInput(new Date())}
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
                    min={currentShift.startTime || toDateTimeLocalInput(new Date())}
                    required
                    className="eo-form-input"
                    value={currentShift.endTime}
                    onChange={(e) => handleShiftTimeChange('endTime', e.target.value)}
                  />
                </div>
              </div>

              {(() => {
                const liveShiftTimeErr = validateShiftTimeRange(
                  currentShift.startTime,
                  currentShift.endTime,
                  createdEvent?.startTime || newEvent.startTime,
                  createdEvent?.endTime || newEvent.endTime,
                  { allowPastStart: false }
                );
                if (!liveShiftTimeErr) return null;
                return (
                  <div
                    style={{
                      backgroundColor: '#fef2f2',
                      border: '1px solid #fecaca',
                      borderRadius: '0.5rem',
                      padding: '0.55rem 0.8rem',
                      fontSize: '0.8rem',
                      color: '#dc2626',
                      marginBottom: '0.85rem',
                      fontWeight: 600,
                    }}
                  >
                    {liveShiftTimeErr}
                  </div>
                );
              })()}

              <ShiftArrivalTimeField
                startTime={currentShift.startTime}
                arrivalTime={currentShift.arrivalTime}
                onChange={(val) => setCurrentShift({ ...currentShift, arrivalTime: val })}
                error={validateShiftArrivalTime(currentShift.arrivalTime, currentShift.startTime)}
              />

              <div className="eo-form-group" style={{ marginBottom: '0.75rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                  <label style={{ margin: 0 }}>Hình Thức Tính Thù Lao *</label>
                  <div style={{ display: 'inline-flex', backgroundColor: '#e2e8f0', padding: '3px', borderRadius: '8px' }}>
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
                      value={currentShift.requiredWorkers ?? ''}
                      onFocus={(e) => e.target.select()}
                      onKeyDown={(e) => {
                        if (e.key === '-' || e.key === 'e') e.preventDefault();
                      }}
                      onChange={(e) => {
                        let raw = e.target.value.replace(/^0+(?=\d)/, '');
                        if (e.target.value !== raw) e.target.value = raw;
                        if (raw === '') {
                          setCurrentShift({ ...currentShift, requiredWorkers: '' });
                          return;
                        }
                        const parsed = parseInt(raw, 10);
                        setCurrentShift({
                          ...currentShift,
                          requiredWorkers: isNaN(parsed) ? '' : Math.max(0, parsed),
                        });
                      }}
                      onBlur={() => {
                        if (!currentShift.requiredWorkers || Number(currentShift.requiredWorkers) < 1) {
                          setCurrentShift((prev) => ({ ...prev, requiredWorkers: 1 }));
                        }
                      }}
                    />
                  </div>

                  {wageMode === 'HOURLY' ? (
                    <div className="eo-form-group" style={{ marginBottom: 0 }}>
                      <label>Mức Thù Lao Theo Giờ (VNĐ/giờ) *</label>
                      <input
                        type="number"
                        min="1"
                        step="any"
                        required
                        className="eo-form-input"
                        style={{
                          borderColor: shiftErrors.hourlyWage ? '#ef4444' : undefined,
                          boxShadow: shiftErrors.hourlyWage ? '0 0 0 1px #ef4444' : undefined,
                        }}
                        value={currentShift.hourlyWage ?? ''}
                        onFocus={(e) => e.target.select()}
                        onKeyDown={(e) => {
                          if (e.key === '-' || e.key === 'e') e.preventDefault();
                        }}
                        onChange={(e) => {
                          let raw = e.target.value.replace(/^0+(?=\d)/, '');
                          if (e.target.value !== raw) e.target.value = raw;
                          const val = raw === '' ? '' : Math.max(0, parseFloat(raw));
                          setCurrentShift({ ...currentShift, hourlyWage: val });
                          setShiftPackageWage(Math.round((Number(val) || 0) * currentDurationHours));
                          if (shiftErrors.hourlyWage) {
                            setShiftErrors((prev) => ({ ...prev, hourlyWage: null }));
                          }
                        }}
                        onBlur={() => {
                          if (currentShift.hourlyWage === '' || isNaN(currentShift.hourlyWage)) {
                            setCurrentShift((prev) => ({ ...prev, hourlyWage: 0 }));
                          }
                        }}
                      />
                      {shiftErrors.hourlyWage ? (
                        <div className="ecw-field-error" style={{ marginTop: '0.25rem', color: '#dc2626', fontSize: '0.78rem', fontWeight: 600 }}>
                          {shiftErrors.hourlyWage}
                        </div>
                      ) : (
                        <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                          = {Math.round(Number(currentShift.hourlyWage || 0) * currentDurationHours).toLocaleString('vi-VN')}đ / ca ({currentDurationHours} giờ)
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="eo-form-group" style={{ marginBottom: 0 }}>
                      <label>Mức Thù Lao Trọn Gói Theo Ca (VNĐ/ca) *</label>
                      <input
                        type="number"
                        min="1"
                        step="any"
                        required
                        className="eo-form-input"
                        style={{
                          borderColor: shiftErrors.hourlyWage ? '#ef4444' : undefined,
                          boxShadow: shiftErrors.hourlyWage ? '0 0 0 1px #ef4444' : undefined,
                        }}
                        value={shiftPackageWage ?? ''}
                        onFocus={(e) => e.target.select()}
                        onKeyDown={(e) => {
                          if (e.key === '-' || e.key === 'e') e.preventDefault();
                        }}
                        onChange={(e) => {
                          let raw = e.target.value.replace(/^0+(?=\d)/, '');
                          if (e.target.value !== raw) e.target.value = raw;
                          handlePackageWageChange(raw);
                          if (shiftErrors.hourlyWage) {
                            setShiftErrors((prev) => ({ ...prev, hourlyWage: null }));
                          }
                        }}
                        onBlur={() => {
                          if (shiftPackageWage === '' || isNaN(shiftPackageWage)) {
                            setShiftPackageWage(0);
                          }
                        }}
                      />
                      {shiftErrors.hourlyWage ? (
                        <div className="ecw-field-error" style={{ marginTop: '0.25rem', color: '#dc2626', fontSize: '0.78rem', fontWeight: 600 }}>
                          {shiftErrors.hourlyWage}
                        </div>
                      ) : (
                        <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                          Quy đổi tương đương: ~{Number(currentShift.hourlyWage || 0).toLocaleString('vi-VN')}đ/giờ ({currentDurationHours} giờ/ca)
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>

              {/* KHUNG THÙ LAO THAM CHIẾU CÓ DẪN CHỨNG PHÁP LÝ & DỮ LIỆU CANI */}
              <WageBenchmarkBox
                jobType={currentShift.jobType}
                jobTypeError={shiftErrors.jobType}
                hourlyWage={currentShift.hourlyWage}
                startTime={currentShift.startTime}
                endTime={currentShift.endTime}
                venueAddress={activeAddress}
                venueLat={activeLat}
                venueLng={activeLng}
                onSelectWage={(chosenWage) => {
                  setCurrentShift((prev) => ({ ...prev, hourlyWage: chosenWage }));
                  setShiftPackageWage(Math.round(chosenWage * currentDurationHours));
                  if (shiftErrors.hourlyWage) {
                    setShiftErrors((prev) => ({ ...prev, hourlyWage: null }));
                  }
                }}
                onBenchmarkLoaded={(bm) => setCurrentShiftBenchmark(bm)}
              />

              <div className="eo-form-row">
                <div className="eo-form-group">
                  <label>Phụ Cấp Thêm (VNĐ)</label>
                  <input
                    type="number"
                    min="0"
                    step="10000"
                    className="eo-form-input"
                    value={currentShift.allowance ?? ''}
                    onFocus={(e) => e.target.select()}
                    onKeyDown={(e) => {
                      if (e.key === '-' || e.key === 'e') e.preventDefault();
                    }}
                    onChange={(e) => {
                      let raw = e.target.value.replace(/^0+(?=\d)/, '');
                      if (e.target.value !== raw) e.target.value = raw;
                      const val = raw === '' ? '' : Math.max(0, parseFloat(raw));
                      setCurrentShift({ ...currentShift, allowance: val });
                    }}
                    onBlur={() => {
                      if (currentShift.allowance === '' || isNaN(currentShift.allowance)) {
                        setCurrentShift((prev) => ({ ...prev, allowance: 0 }));
                      }
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
                    value={currentShift.maxDistanceKm ?? ''}
                    onFocus={(e) => e.target.select()}
                    onKeyDown={(e) => {
                      if (e.key === '-' || e.key === 'e') e.preventDefault();
                    }}
                    onChange={(e) => {
                      let raw = e.target.value.replace(/^0+(?=\d)/, '');
                      if (e.target.value !== raw) e.target.value = raw;
                      if (raw === '') {
                        setCurrentShift({ ...currentShift, maxDistanceKm: '' });
                        return;
                      }
                      setCurrentShift({
                        ...currentShift,
                        maxDistanceKm: Math.max(1, Math.min(50, parseFloat(raw) || 15)),
                      });
                    }}
                    onBlur={() => {
                      if (!currentShift.maxDistanceKm || Number(currentShift.maxDistanceKm) < 1) {
                        setCurrentShift((prev) => ({ ...prev, maxDistanceKm: 15 }));
                      }
                    }}
                  />
                </div>
              </div>

              {/* ĐÁNH GIÁ KHẢ NĂNG TUYỂN DỤNG */}
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
                  borderRadius: '0.6rem',
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
                    {riskAnalysis.label}
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
                        padding: '0.3rem 0.7rem',
                        borderRadius: '6px',
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

              <ShiftSkillSelector
                jobType={currentShift.jobType}
                selectedSkillIds={currentShift.requiredSkills}
                onChange={(nextSkills) => setCurrentShift((prev) => ({ ...prev, requiredSkills: nextSkills }))}
                performedSkillId={currentShift.performedSkillId}
                onPerformedSkillChange={(nextId) => setCurrentShift((prev) => ({ ...prev, performedSkillId: nextId }))}
                skillRequirements={currentShift.skillRequirements}
                onSkillRequirementsChange={(nextReqs) => setCurrentShift((prev) => ({ ...prev, skillRequirements: nextReqs }))}
                customSkillNotes={currentShift.customSkillNotes}
                onCustomSkillNotesChange={(nextNotes) => setCurrentShift((prev) => ({ ...prev, customSkillNotes: nextNotes }))}
                taxonomy={skillTaxonomy}
              />

              <ShiftChecklistEditor
                items={currentShift.checklistItems}
                onChange={(updatedItems) =>
                  setCurrentShift((prev) => ({
                    ...prev,
                    checklistItems: updatedItems,
                  }))
                }
                jobType={currentShift.jobType}
              />

              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.35rem',
                  padding: '0.85rem 1rem',
                  backgroundColor: '#eff6ff',
                  borderRadius: '0.6rem',
                  marginBottom: '1rem',
                  border: '1px solid #bfdbfe',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.875rem', color: '#1e40af' }}>
                    Dự toán thù lao ca này: <strong>{currentDurationHours}h x {currentShift.requiredWorkers} người</strong>
                  </span>
                  <span style={{ fontSize: '1.15rem', fontWeight: 800, color: '#1d4ed8' }}>
                    {currentShiftTotal.toLocaleString('vi-VN')} đ
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
                      setShiftErrors({});
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
          )}

          <div className="eo-modal-footer">
            <button type="button" className="eo-btn-secondary" onClick={() => setCurrentStep(1)}>
              Quay Lại Bước 1
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
              Tiếp Tục Sang Bước 3
            </button>
          </div>
        </div>
      )}

      {/* BƯỚC 3: XÁC NHẬN & KÍCH HOẠT */}
      {currentStep === 3 && (
        <div className="animate-fade-in-up">
          <h3 style={{ margin: '0 0 0.4rem', fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>
            Bước 3: Xác Nhận Thông Tin & Kích Hoạt Tìm Kiếm Nhân Sự
          </h3>
          <p style={{ fontSize: '0.86rem', color: '#64748b', margin: '0 0 1.25rem' }}>
            Kiểm tra lại toàn bộ thông tin sự kiện, địa chỉ và các ca làm việc trước khi lưu bản nháp hoặc đăng tuyển ngay.
          </p>

          {(newEvent.imageUrl || createdEvent?.imageUrl) && (
            <div style={{ marginBottom: '1.25rem', borderRadius: '0.75rem', overflow: 'hidden', border: '1px solid #cbd5e1', backgroundColor: '#f8fafc' }}>
              <img
                src={newEvent.imageUrl || createdEvent?.imageUrl}
                alt={createdEvent?.name || newEvent.name}
                style={{ width: '100%', maxHeight: '240px', objectFit: 'cover', display: 'block' }}
                onError={(e) => {
                  e.target.style.display = 'none';
                }}
              />
            </div>
          )}

          <div style={{ backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem', gap: '1rem' }}>
              <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tên sự kiện:</span>
              <strong style={{ color: '#0f172a', textAlign: 'right' }}>{createdEvent?.name || newEvent.name}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem', gap: '1rem' }}>
              <span style={{ color: '#64748b', fontSize: '0.875rem', whiteSpace: 'nowrap' }}>Địa chỉ tổ chức:</span>
              <span style={{ fontWeight: 600, color: '#1e293b', textAlign: 'right' }}>
                {activeAddress || 'Chưa cập nhật địa chỉ cụ thể'}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem', gap: '1rem' }}>
              <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Thời gian tổ chức:</span>
              <span style={{ fontWeight: 600, color: '#1e293b', textAlign: 'right' }}>
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
            <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '0.6rem', borderTop: '1px dashed #cbd5e1' }}>
              <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tổng dự toán thù lao ca:</span>
              <strong style={{ color: '#16a34a', fontSize: '1.08rem' }}>{totalEstimatedCost.toLocaleString('vi-VN')} đ</strong>
            </div>
          </div>

          <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '0.75rem', padding: '1.1rem', marginBottom: '1.5rem' }}>
            <div style={{ fontWeight: 700, color: '#15803d', marginBottom: '0.35rem' }}>
              Chính Sách Gói Dịch Vụ & Hạn Mức Sự Kiện
            </div>
            <p style={{ fontSize: '0.83rem', color: '#166534', margin: 0, lineHeight: 1.5 }}>
              Khi bấm <strong>Đăng Tuyển &amp; Tìm Người Ngay</strong>, hệ thống sẽ trừ <strong>1 sự kiện</strong> trong gói dịch vụ của bạn. Nếu bấm <strong>Lưu Bản Nháp</strong>, sự kiện sẽ được lưu lại mà chưa trừ lượt gói cước.
            </p>
          </div>

          <div className="eo-modal-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            <button type="button" className="eo-btn-secondary" onClick={() => setCurrentStep(2)} disabled={submitting}>
              Quay Lại Sửa Ca
            </button>

            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={handleSaveAsDraft}
                disabled={submitting}
                style={{
                  background: '#fffbeb',
                  color: '#b45309',
                  border: '1px solid #fcd34d',
                  borderRadius: '0.55rem',
                  padding: '0.75rem 1.35rem',
                  fontSize: '0.9rem',
                  fontWeight: 700,
                  cursor: submitting ? 'not-allowed' : 'pointer',
                }}
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
    </>
  );

  // ========================================================================
  // CHẾ ĐỘ TRANG WORKSPACE RIÊNG (asPage = true)
  // ========================================================================
  if (asPage) {
    return (
      <div className="ecw-page-container">
        <header className="ecw-header-card">
          <div className="ecw-header-top">
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
              <button type="button" className="ecw-back-btn" onClick={handleRequestCancelWizard}>
                &larr; Quay lại danh sách sự kiện
              </button>
              <div className="ecw-header-titles">
                <span className="ecw-eyebrow">Quy trình thiết lập 3 bước</span>
                <h1>{createdEvent?.name || newEvent.name || 'Tạo Sự Kiện & Ca Làm Việc Mới'}</h1>
              </div>
            </div>

            {createdEvent?.eventId && (
              <span className="ecw-draft-pill">Đã lưu bản nháp sự kiện</span>
            )}
          </div>

          <div className="ecw-stepper">
            {stepItems.map((st) => {
              const isActive = currentStep === st.num;
              const isPassed = currentStep > st.num;
              const clickable = canJumpToStep(st.num);
              return (
                <button
                  key={st.num}
                  type="button"
                  disabled={!clickable}
                  onClick={() => clickable && setCurrentStep(st.num)}
                  className={`ecw-step-item ${isActive ? 'is-active' : ''} ${isPassed ? 'is-passed' : ''} ${clickable ? 'is-clickable' : ''}`}
                >
                  <span className="ecw-step-num">{isPassed ? '✓' : st.num}</span>
                  <span className="ecw-step-meta">
                    <span className="ecw-step-title">{st.num}. {st.title}</span>
                    <span className="ecw-step-sub">{st.sub}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </header>

        <div className="ecw-workspace-grid">
          <div className="ecw-main-card">{wizardMainContent}</div>

          <aside className="ecw-summary-card" aria-label="Tóm tắt sự kiện">
            <h2 className="ecw-summary-title">
              <span>Tóm Tắt Sự Kiện</span>
              <span style={{ fontSize: '0.74rem', color: '#2563eb', fontWeight: 700 }}>
                Bước {currentStep}/3
              </span>
            </h2>

            {(newEvent.imageUrl || createdEvent?.imageUrl) && (
              <div className="ecw-summary-poster">
                <img
                  src={newEvent.imageUrl || createdEvent?.imageUrl}
                  alt="Poster sự kiện"
                  onError={(e) => {
                    e.target.style.display = 'none';
                  }}
                />
              </div>
            )}

            <div className="ecw-summary-rows">
              <div className="ecw-summary-row">
                <span className="ecw-summary-label">Tên sự kiện</span>
                <span className="ecw-summary-val">
                  {createdEvent?.name || newEvent.name || 'Chưa đặt tên'}
                </span>
              </div>
              <div className="ecw-summary-row">
                <span className="ecw-summary-label">Thời gian</span>
                <span className="ecw-summary-val">
                  {formatDateTimeDisplay(createdEvent?.startTime || newEvent.startTime)} &rarr;{' '}
                  {formatDateTimeDisplay(createdEvent?.endTime || newEvent.endTime)}
                </span>
              </div>
              <div className="ecw-summary-row">
                <span className="ecw-summary-label">Địa điểm &amp; Vùng lương</span>
                <span className="ecw-summary-val">
                  {activeAddress || (regionInfo.isResolved ? 'Đã ghim vị trí trên bản đồ (chưa nhập tên đường)' : 'Chưa chọn địa chỉ')}
                </span>
                <span style={{ fontSize: '0.73rem', color: regionInfo.isResolved ? '#0369a1' : '#64748b', fontWeight: 600 }}>
                  {regionInfo.isResolved
                    ? `${regionInfo.regionLabel} • Sàn ${regionInfo.wage.toLocaleString('vi-VN')}đ/h`
                    : 'Chưa xác định khu vực (chọn địa điểm để áp dụng sàn lương)'}
                </span>
              </div>
            </div>

            <div className="ecw-summary-kpis">
              <div className="ecw-summary-kpi">
                <span>Số ca đã tạo</span>
                <strong>{shiftsList.length} ca</strong>
              </div>
              <div className="ecw-summary-kpi">
                <span>Tổng nhân sự</span>
                <strong>{totalWorkers} người</strong>
              </div>
              <div className="ecw-summary-kpi is-full">
                <span>Dự toán thù lao toàn sự kiện</span>
                <strong className="is-money">{totalEstimatedCost.toLocaleString('vi-VN')} đ</strong>
              </div>
            </div>

            {shiftsList.length > 0 && (
              <div className="ecw-summary-shifts-mini">
                <span className="ecw-summary-label">Các ca đã thêm</span>
                {shiftsList.map((s, i) => (
                  <div key={s.shiftId || i} className="ecw-summary-shift-item">
                    <span style={{ fontWeight: 600, color: '#0f172a', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      #{i + 1} {s.jobType}
                    </span>
                    <span style={{ color: '#2563eb', fontWeight: 700, flexShrink: 0 }}>
                      {s.requiredWorkers} người
                    </span>
                  </div>
                ))}
              </div>
            )}
          </aside>
        </div>
      </div>
    );
  }

  // ========================================================================
  // CHẾ ĐỘ POPUP MODAL FALLBACK (khi asPage = false)
  // ========================================================================
  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box eo-modal-box--wide" style={{ maxWidth: '1060px', maxHeight: '92vh', overflowY: 'auto' }}>
        <div style={{ marginBottom: '1.25rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Quy trình thiết lập sự kiện &amp; ca làm việc
            </span>
            <button
              type="button"
              onClick={handleClose}
              style={{ background: 'none', border: 'none', fontSize: '1rem', cursor: 'pointer', color: '#64748b', fontWeight: 600 }}
            >
              Đóng
            </button>
          </div>
          <div className="ecw-stepper">
            {stepItems.map((st) => {
              const isActive = currentStep === st.num;
              const isPassed = currentStep > st.num;
              return (
                <div key={st.num} className={`ecw-step-item ${isActive ? 'is-active' : ''} ${isPassed ? 'is-passed' : ''}`}>
                  <span className="ecw-step-num">{isPassed ? '✓' : st.num}</span>
                  <span className="ecw-step-meta">
                    <span className="ecw-step-title">{st.num}. {st.title}</span>
                  </span>
                </div>
              );
            })}
          </div>
        </div>
        {wizardMainContent}
      </div>
    </div>
  );
};

export default EventCreationWizardModal;
