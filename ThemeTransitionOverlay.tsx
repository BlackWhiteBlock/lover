/**
 * ThemeTransitionOverlay
 *
 * 单身 → 恋爱 主题切换动画：
 *   1. 两颗心从屏幕左右飞入
 *   2. 中心碰撞 → 玫瑰色光爆 + 涟漪
 *   3. 玫瑰色从中心铺满全屏
 *   4. 小爱心粒子向四周散开
 *   5. 色幕淡出，新主题显现
 */

import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';

interface Props {
  onComplete: () => void;
}

const HEART = 'M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z';

// Tiny scattered hearts that fly out from the collision
const PARTICLES = [
  { angle: -70,  delay: 0,    dist: 160, size: 14, color: '#fb7185' },
  { angle: -20,  delay: 0.05, dist: 200, size: 10, color: '#fda4af' },
  { angle: 20,   delay: 0.02, dist: 190, size: 16, color: '#fb7185' },
  { angle: 70,   delay: 0.08, dist: 150, size: 11, color: '#f43f5e' },
  { angle: 110,  delay: 0.04, dist: 180, size: 13, color: '#fda4af' },
  { angle: 160,  delay: 0.07, dist: 170, size: 9,  color: '#fb7185' },
  { angle: -120, delay: 0.03, dist: 185, size: 15, color: '#f43f5e' },
  { angle: -160, delay: 0.06, dist: 155, size: 10, color: '#fda4af' },
  { angle: -45,  delay: 0.09, dist: 210, size: 12, color: '#fb7185' },
  { angle: 45,   delay: 0.01, dist: 195, size: 8,  color: '#fda4af' },
  { angle: 135,  delay: 0.05, dist: 165, size: 14, color: '#f43f5e' },
  { angle: -135, delay: 0.03, dist: 175, size: 11, color: '#fb7185' },
];

const IMPACT = 0.9; // seconds until hearts collide

const ThemeTransitionOverlay: React.FC<Props> = ({ onComplete }) => {
  const [phase, setPhase] = useState<'approach' | 'burst' | 'fill' | 'done'>('approach');

  useEffect(() => {
    const t1 = setTimeout(() => setPhase('burst'), IMPACT * 1000);
    const t2 = setTimeout(() => setPhase('fill'),  (IMPACT + 0.15) * 1000);
    const t3 = setTimeout(() => setPhase('done'),  (IMPACT + 1.6)  * 1000);
    const t4 = setTimeout(onComplete,              (IMPACT + 2.4)  * 1000);
    return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); clearTimeout(t4); };
  }, [onComplete]);

  return (
    <motion.div
      className="fixed inset-0 z-[9999] flex items-center justify-center overflow-hidden pointer-events-none"
      initial={{ opacity: 1 }}
      animate={{ opacity: phase === 'done' ? 0 : 1 }}
      transition={{ duration: 0.8, ease: 'easeInOut', delay: phase === 'done' ? 0.1 : 0 }}
    >
      {/* Rose fill — expands from center after impact */}
      <motion.div
        className="absolute rounded-full"
        style={{ background: 'radial-gradient(circle, #fff0f2 0%, #ffe4e8 30%, #fecdd3 70%, #fb7185 100%)' }}
        initial={{ width: 0, height: 0, opacity: 0 }}
        animate={phase === 'fill' || phase === 'done'
          ? { width: '300vmax', height: '300vmax', opacity: 1 }
          : {}}
        transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
      />

      {/* Left heart */}
      <motion.svg
        viewBox="0 0 24 24"
        className="absolute"
        style={{ width: 52, height: 52, filter: 'drop-shadow(0 0 12px rgba(251,113,133,0.6))' }}
        initial={{ x: '-48vw', opacity: 0, scale: 0.8 }}
        animate={phase === 'approach'
          ? { x: -32, opacity: 1, scale: 1 }
          : { x: -32, opacity: phase === 'burst' ? 1 : 0, scale: phase === 'burst' ? 1.4 : 0.3 }}
        transition={phase === 'approach'
          ? { duration: IMPACT, ease: [0.4, 0, 0.2, 1], opacity: { duration: 0.3 } }
          : { duration: 0.25, ease: 'easeOut' }}
      >
        <path d={HEART} fill="#fb7185" />
      </motion.svg>

      {/* Right heart */}
      <motion.svg
        viewBox="0 0 24 24"
        className="absolute"
        style={{ width: 52, height: 52, filter: 'drop-shadow(0 0 12px rgba(251,113,133,0.6))' }}
        initial={{ x: '48vw', opacity: 0, scale: 0.8 }}
        animate={phase === 'approach'
          ? { x: 32, opacity: 1, scale: 1 }
          : { x: 32, opacity: phase === 'burst' ? 1 : 0, scale: phase === 'burst' ? 1.4 : 0.3 }}
        transition={phase === 'approach'
          ? { duration: IMPACT, ease: [0.4, 0, 0.2, 1], opacity: { duration: 0.3, delay: 0.05 } }
          : { duration: 0.25, ease: 'easeOut' }}
      >
        <path d={HEART} fill="#fb7185" />
      </motion.svg>

      {/* Impact glow */}
      <AnimatePresence>
        {(phase === 'burst' || phase === 'fill') && (
          <motion.div
            className="absolute rounded-full"
            style={{ width: 80, height: 80, background: 'radial-gradient(circle, rgba(255,255,255,0.95), rgba(251,113,133,0.6))', filter: 'blur(8px)' }}
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: [0, 2.8, 1.6], opacity: [0, 1, 0] }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.6, ease: 'easeOut' }}
          />
        )}
      </AnimatePresence>

      {/* Merged double heart — appears after burst on the fill layer */}
      <AnimatePresence>
        {(phase === 'fill' || phase === 'done') && (
          <motion.div
            className="absolute flex items-center justify-center"
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: [0, 1.3, 1], opacity: 1 }}
            transition={{ duration: 0.5, ease: [0.34, 1.56, 0.64, 1] }}
          >
            <svg viewBox="0 0 100 96" fill="none" style={{ width: 72, height: 72 }}>
              <path
                d="M 0 0 C -10 -18, -34 -18, -34 -38 C -34 -58, -17 -66, 0 -50 C 17 -66, 34 -58, 34 -38 C 34 -18, 10 -18, 0 0 Z"
                fill="#fb7185" fillOpacity={0.45}
                transform="translate(50, 84) rotate(15)"
              />
              <path
                d="M 0 0 C -10 -18, -34 -18, -34 -38 C -34 -58, -17 -66, 0 -50 C 17 -66, 34 -58, 34 -38 C 34 -18, 10 -18, 0 0 Z"
                fill="#fb7185" fillOpacity={0.45}
                transform="translate(50, 84) rotate(-15)"
              />
            </svg>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Particle hearts */}
      <AnimatePresence>
        {(phase === 'burst' || phase === 'fill') && PARTICLES.map(({ angle, delay, dist, size, color }, i) => {
          const rad = (angle * Math.PI) / 180;
          const tx = Math.cos(rad) * dist;
          const ty = Math.sin(rad) * dist;
          return (
            <motion.svg
              key={i}
              viewBox="0 0 24 24"
              className="absolute"
              style={{ width: size, height: size, originX: '50%', originY: '50%' }}
              initial={{ x: 0, y: 0, opacity: 0, scale: 0, rotate: angle }}
              animate={{ x: tx, y: ty, opacity: [0, 1, 1, 0], scale: [0, 1.2, 1, 0.4], rotate: angle + (Math.random() > 0.5 ? 40 : -40) }}
              transition={{ duration: 1.2, delay, ease: [0.2, 0.8, 0.4, 1] }}
            >
              <path d={HEART} fill={color} />
            </motion.svg>
          );
        })}
      </AnimatePresence>

      {/* Sweet text that fades in over the fill */}
      <AnimatePresence>
        {(phase === 'fill' || phase === 'done') && (
          <motion.div
            className="absolute flex flex-col items-center gap-2"
            style={{ bottom: '38%' }}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ delay: 0.3, duration: 0.6, ease: 'easeOut' }}
          >
            <p style={{
              fontFamily: "'Pinyon Script', cursive",
              fontSize: '2rem',
              color: '#be185d',
              opacity: 0.8,
              lineHeight: 1,
            }}>
              欢迎你来到“我们”的空间 ♡
            </p>
            <p style={{
              fontSize: '10px',
              color: '#f43f5e',
              opacity: 0.6,
              letterSpacing: '0.4em',
              textTransform: 'uppercase',
              fontFamily: "'Lato', sans-serif",
              fontWeight: 300,
            }}>
              You found each other
            </p>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
};

export default ThemeTransitionOverlay;
