import React from 'react';
import { CORE_PLAN_FEATURES } from '../types/adminPlans.constants';

export default function CoreFeaturesChecklist({ selectedFeatures, onToggleFeature }) {
  return (
    <div className="core-features-checklist">
      {CORE_PLAN_FEATURES.map((feat, idx) => {
        const checked = selectedFeatures.includes(feat);
        return (
          <label key={idx} className={`checklist-item ${checked ? 'item-checked' : ''}`}>
            <input
              type="checkbox"
              checked={checked}
              onChange={() => onToggleFeature(feat)}
            />
            <span>{feat}</span>
          </label>
        );
      })}
    </div>
  );
}
