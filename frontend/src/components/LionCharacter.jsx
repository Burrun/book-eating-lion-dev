import { useEffect, useState } from "react";
import { motion } from "framer-motion";

// 레벨 구간별 성장 단계 + 확정된 디자인 시안의 지오메트리(반경/갈기 개수/왕관 유무).
// 얼굴/꼬리/귀 등 부속 파츠는 머리 반경(headR) 대비 아기 사자(headR=30) 비율로 스케일된다.
const TIER_GEOMETRY = {
  baby: {
    key: "baby",
    label: "아기 사자",
    bodyRx: 34,
    bodyRy: 30,
    headR: 30,
    maneCount: 0,
    hasCrown: false,
  },
  cub: {
    key: "cub",
    label: "새끼 사자",
    bodyRx: 40,
    bodyRy: 35,
    headR: 34,
    maneCount: 7,
    hasCrown: false,
  },
  adult: {
    key: "adult",
    label: "대양 사자",
    bodyRx: 46,
    bodyRy: 40,
    headR: 40,
    maneCount: 12,
    hasCrown: true,
  },
};
const BASE_HEAD_R = TIER_GEOMETRY.baby.headR;

export function getLionTier(level) {
  if (level <= 2) return TIER_GEOMETRY.baby;
  if (level <= 4) return TIER_GEOMETRY.cub;
  return TIER_GEOMETRY.adult;
}

// 렌더 중 Math.random() 호출은 순수성 규칙(react-hooks/purity)에 걸리므로,
// setTimeout 콜백 안에서만 난수를 뽑아 "가끔 눈을 깜빡이고 귀를 쫑긋거리는" 느낌을 낸다.
function useRandomPulse(minMs, maxMs, activeMs) {
  const [active, setActive] = useState(false);

  useEffect(() => {
    let scheduleId;
    let activeId;
    let cancelled = false;

    const scheduleNext = () => {
      const delay = minMs + Math.random() * (maxMs - minMs);
      scheduleId = setTimeout(() => {
        if (cancelled) return;
        setActive(true);
        activeId = setTimeout(() => {
          if (!cancelled) setActive(false);
        }, activeMs);
        scheduleNext();
      }, delay);
    };

    scheduleNext();
    return () => {
      cancelled = true;
      clearTimeout(scheduleId);
      clearTimeout(activeId);
    };
  }, [minMs, maxMs, activeMs]);

  return active;
}

function crownPath(cx, topY, width, height) {
  const left = cx - width / 2;
  const right = cx + width / 2;
  const baseY = topY + height;
  const midY = topY + height * 0.55;
  return `M ${left} ${baseY} L ${left} ${midY} L ${left + width * 0.2} ${topY + height * 0.75} L ${
    left + width * 0.35
  } ${midY} L ${cx} ${topY} L ${left + width * 0.65} ${midY} L ${right - width * 0.2} ${
    topY + height * 0.75
  } L ${right} ${midY} L ${right} ${baseY} Z`;
}

const VIEWBOX_W = 140;
const VIEWBOX_H = 190;

export default function LionCharacter({
  level,
  isFeeding = false,
  isLevelingUp = false,
  crownEntrance = false,
  size = 100,
}) {
  const tier = getLionTier(level);
  const scale = tier.headR / BASE_HEAD_R; // 귀/얼굴/꼬리 등 부속 파츠에 적용하는 공통 확대 비율

  const blinking = useRandomPulse(3000, 5000, 150);
  const earTwitch = useRandomPulse(4000, 6000, 400);

  const cx = VIEWBOX_W / 2;
  const groundY = 178;
  const bodyCenterY = groundY - 8 - tier.bodyRy;
  const headCenterY = bodyCenterY - tier.bodyRy - tier.headR * 0.55;
  const headTop = headCenterY - tier.headR;

  const earOuterR = 12 * scale;
  const earInnerR = 6 * scale;
  const earOffsetX = tier.headR * 0.72;
  const earOffsetY = tier.headR * 0.62;

  const faceRx = 19 * scale;
  const faceRy = 14 * scale;
  const faceCenterY = headCenterY + tier.headR * 0.22;

  const eyeOffsetX = faceRx * 0.45;
  const eyeY = faceCenterY - faceRy * 0.3;

  const blushOffsetX = faceRx * 1.35;
  const blushR = 6 * scale;

  const tailStartX = cx + tier.bodyRx * 0.72;
  const tailStartY = bodyCenterY - tier.bodyRy * 0.2;
  const tailPomX = cx + tier.bodyRx * 1.28;
  const tailPomY = headCenterY + tier.headR * 0.25;
  const tailPomR = 6 * scale;

  const maneCircleR = tier.headR * 0.32;
  const maneRadius = tier.headR * 1.08;

  const wrapperAnimate = isLevelingUp
    ? { y: [0, -16, 0] }
    : isFeeding
      ? { scale: [1, 1.06, 1], rotate: [0, -3, 3, 0] }
      : { y: 0, scale: 1, rotate: 0 };

  return (
    <div
      className="relative flex items-center justify-center"
      style={{ width: size, height: size * (VIEWBOX_H / VIEWBOX_W) }}
    >
      <motion.div
        animate={wrapperAnimate}
        transition={{ duration: isLevelingUp ? 0.6 : 0.5, repeat: isLevelingUp ? Infinity : 0 }}
        className="h-full w-full"
      >
        <svg viewBox={`0 0 ${VIEWBOX_W} ${VIEWBOX_H}`} width="100%" height="100%">
          {/* 그림자 */}
          <ellipse
            cx={cx}
            cy={groundY}
            rx={tier.bodyRx * 1.05}
            ry={tier.bodyRy * 0.28}
            fill="var(--color-forest)"
            opacity="0.08"
          />

          {/* 꼬리 (곡선 + 끝 폼폼) */}
          <motion.g
            animate={{ rotate: [-5, 5, -5] }}
            transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
            style={{ transformOrigin: `${tailStartX}px ${tailStartY}px` }}
          >
            <path
              d={`M ${tailStartX} ${tailStartY} Q ${tailStartX + tier.bodyRx * 0.55} ${tailStartY - tier.bodyRy * 0.1} ${tailPomX} ${tailPomY}`}
              stroke="var(--color-honey)"
              strokeWidth={7 * scale}
              fill="none"
              strokeLinecap="round"
            />
            <circle cx={tailPomX} cy={tailPomY} r={tailPomR} fill="#E8563F" />
          </motion.g>

          {/* 몸통 (숨쉬기 애니메이션) */}
          <motion.ellipse
            cx={cx}
            cy={bodyCenterY}
            rx={tier.bodyRx}
            ry={tier.bodyRy}
            fill="#F2A93B"
            animate={{ scaleY: [1, 1.02, 1] }}
            transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut" }}
            style={{ transformOrigin: `${cx}px ${groundY - 8}px` }}
          />

          {/* 갈기 */}
          {Array.from({ length: tier.maneCount }).map((_, i) => {
            const spread = tier.key === "cub" ? Math.PI * 0.95 : Math.PI * 2;
            const startAngle = tier.key === "cub" ? -Math.PI * 0.97 : -Math.PI / 2;
            const angle =
              tier.maneCount === 1
                ? startAngle
                : startAngle + (i / (tier.maneCount - 1 || 1)) * spread;
            const mcx = cx + Math.cos(angle) * maneRadius;
            const mcy = headCenterY + Math.sin(angle) * maneRadius;
            return <circle key={i} cx={mcx} cy={mcy} r={maneCircleR} fill="#D9781F" />;
          })}

          {/* 왕관 (대양 사자 전용) */}
          {tier.hasCrown && (
            <motion.g
              initial={crownEntrance ? { y: -40, opacity: 0 } : false}
              animate={{ y: 0, opacity: 1 }}
              transition={
                crownEntrance ? { type: "spring", stiffness: 300, damping: 12 } : { duration: 0 }
              }
            >
              <path
                d={crownPath(cx, headTop - tier.headR * 0.62, tier.headR * 1.05, tier.headR * 0.5)}
                fill="#F2A93B"
                stroke="#D9781F"
                strokeWidth="2"
                strokeLinejoin="round"
              />
              <circle
                cx={cx - tier.headR * 0.4}
                cy={headTop - tier.headR * 0.42}
                r={4 * scale}
                fill="#E8563F"
              />
              <circle cx={cx} cy={headTop - tier.headR * 0.6} r={4.5 * scale} fill="#E8563F" />
              <circle
                cx={cx + tier.headR * 0.4}
                cy={headTop - tier.headR * 0.42}
                r={4 * scale}
                fill="#E8563F"
              />
            </motion.g>
          )}

          {/* 귀 (쫑긋 애니메이션) */}
          <motion.g
            animate={{ rotate: earTwitch ? [0, -14, 0] : 0 }}
            transition={{ duration: 0.4 }}
            style={{ transformOrigin: `${cx - earOffsetX}px ${headCenterY - earOffsetY}px` }}
          >
            <circle
              cx={cx - earOffsetX}
              cy={headCenterY - earOffsetY}
              r={earOuterR}
              fill="#F2A93B"
            />
            <circle
              cx={cx - earOffsetX}
              cy={headCenterY - earOffsetY}
              r={earInnerR}
              fill="#FBE0B8"
            />
          </motion.g>
          <motion.g
            animate={{ rotate: earTwitch ? [0, 14, 0] : 0 }}
            transition={{ duration: 0.4 }}
            style={{ transformOrigin: `${cx + earOffsetX}px ${headCenterY - earOffsetY}px` }}
          >
            <circle
              cx={cx + earOffsetX}
              cy={headCenterY - earOffsetY}
              r={earOuterR}
              fill="#F2A93B"
            />
            <circle
              cx={cx + earOffsetX}
              cy={headCenterY - earOffsetY}
              r={earInnerR}
              fill="#FBE0B8"
            />
          </motion.g>

          {/* 머리 */}
          <circle cx={cx} cy={headCenterY} r={tier.headR} fill="#F2A93B" />

          {/* 볼터치 */}
          <circle
            cx={cx - blushOffsetX}
            cy={faceCenterY + faceRy * 0.25}
            r={blushR}
            fill="#E8563F"
            opacity="0.35"
          />
          <circle
            cx={cx + blushOffsetX}
            cy={faceCenterY + faceRy * 0.25}
            r={blushR}
            fill="#E8563F"
            opacity="0.35"
          />

          {/* 얼굴 patch */}
          <ellipse cx={cx} cy={faceCenterY} rx={faceRx} ry={faceRy} fill="#FBE0B8" />

          {/* 눈 (깜빡임) */}
          <motion.ellipse
            cx={cx - eyeOffsetX}
            cy={eyeY}
            rx={3.2 * scale}
            fill="#2D2A26"
            animate={{ ry: blinking ? 0.5 : 3.2 * scale }}
            transition={{ duration: 0.1 }}
          />
          <motion.ellipse
            cx={cx + eyeOffsetX}
            cy={eyeY}
            rx={3.2 * scale}
            fill="#2D2A26"
            animate={{ ry: blinking ? 0.5 : 3.2 * scale }}
            transition={{ duration: 0.1 }}
          />

          {/* 코 */}
          <circle cx={cx} cy={eyeY + faceRy * 0.55} r={2.6 * scale} fill="#2D2A26" />

          {/* 입 (먹이기 모션) */}
          <motion.path
            d={`M ${cx - 6 * scale} ${eyeY + faceRy * 0.85} Q ${cx} ${eyeY + faceRy * 1.15} ${cx + 6 * scale} ${eyeY + faceRy * 0.85}`}
            stroke="#2D2A26"
            strokeWidth={1.5 * scale}
            fill="none"
            strokeLinecap="round"
            animate={isFeeding ? { scaleY: [1, 2.2, 1, 2.2, 1] } : { scaleY: 1 }}
            transition={{ duration: isFeeding ? 0.6 : 0.3 }}
            style={{ transformOrigin: `${cx}px ${eyeY + faceRy}px` }}
          />
        </svg>
      </motion.div>
    </div>
  );
}
