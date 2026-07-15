import React, { useEffect } from 'react';
import { motion } from 'motion/react';

const HEART_PATH = "M 0 0 C -10 -18, -34 -18, -34 -38 C -34 -58, -17 -66, 0 -50 C 17 -66, 34 -58, 34 -38 C 34 -18, 10 -18, 0 0 Z";

// Tip sits at SVG coordinate (50, 84) inside a 100×96 viewBox → 87.5% from top
const TIP_ORIGIN = '50% 87.5%';

const APPROACH_DELAY = 0.55;
const APPROACH_DUR   = 0.95;
const IMPACT         = APPROACH_DELAY + APPROACH_DUR; // ~1.5 s

const SVG_SIZE = 148; // rendered px

interface SplashScreenProps {
  onComplete: () => void;
}

const SplashScreen: React.FC<SplashScreenProps> = ({ onComplete }) => {
  useEffect(() => {
    const t = setTimeout(onComplete, 4400);
    return () => clearTimeout(t);
  }, [onComplete]);

  const heartStyle: React.CSSProperties = {
    position: 'absolute',
    left: '50%',
    top: 0,
    marginLeft: -SVG_SIZE / 2,
    width: SVG_SIZE,
    height: SVG_SIZE * (96 / 100),
    transformBox: 'fill-box' as any,
    transformOrigin: TIP_ORIGIN,
  };

  const rippleBase: React.CSSProperties = {
    position: 'absolute',
    left: '50%',
    bottom: 0,
    width: 56,
    height: 56,
    marginLeft: -28,
    marginBottom: -28,
    borderRadius: '50%',
    transformOrigin: 'center',
  };

  return (
    <motion.div
      className="fixed inset-0 bg-[#fffcfb] flex flex-col items-center justify-center gap-10 overflow-hidden"
      initial={{ opacity: 1 }}
      animate={{ opacity: [1, 1, 1, 0] }}
      transition={{ duration: 4.4, times: [0, 0.62, 0.78, 1], ease: 'easeInOut' }}
    >
      {/* Breathing background orb */}
      <motion.div
        className="absolute w-96 h-96 bg-rose-50 rounded-full blur-3xl pointer-events-none"
        animate={{ scale: [1, 1.25, 1] }}
        transition={{ duration: 3.5, repeat: Infinity, ease: 'easeInOut' }}
      />

      {/* Hearts stage */}
      <div
        className="relative"
        style={{ width: SVG_SIZE * 2, height: SVG_SIZE * (96 / 100) }}
      >
        {/* Ripple rings — emanate from the tip meeting point */}
        {[
          { delay: IMPACT,        size: 2.8, color: 'rgba(251,113,133,0.5)' },
          { delay: IMPACT + 0.14, size: 3.8, color: 'rgba(251,113,133,0.3)' },
          { delay: IMPACT + 0.30, size: 5.0, color: 'rgba(251,113,133,0.18)' },
        ].map(({ delay, size, color }, i) => (
          <motion.div
            key={i}
            style={{ ...rippleBase, border: `1.5px solid ${color}` }}
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: [0, 0.01, size], opacity: [0, 1, 0] }}
            transition={{ delay, duration: 1.3, times: [0, 0.01, 1], ease: 'easeOut' }}
          />
        ))}

        {/* Warm glow burst at tip */}
        <motion.div
          className="absolute bg-rose-300 rounded-full blur-2xl pointer-events-none"
          style={{ ...rippleBase, width: 80, height: 80, marginLeft: -40, marginBottom: -40 }}
          initial={{ scale: 0, opacity: 0 }}
          animate={{ scale: [0, 0, 1.4, 1.8, 0], opacity: [0, 0, 0.65, 0.3, 0] }}
          transition={{ delay: IMPACT, duration: 1.0, times: [0, 0.01, 0.22, 0.55, 1] }}
        />

        {/* Left heart */}
        <motion.svg
          viewBox="0 0 100 96"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          style={heartStyle}
          initial={{ x: -SVG_SIZE * 0.95, opacity: 0, rotate: 0, scale: 1 }}
          animate={{
            x: 0,
            opacity: 1,
            rotate: 15,
            scale: [1, 1, 1, 1.22, 0.87, 1.06, 1],
          }}
          transition={{
            x:       { delay: APPROACH_DELAY,      duration: APPROACH_DUR, ease: [0.22, 1, 0.36, 1] },
            opacity: { delay: APPROACH_DELAY - 0.1, duration: 0.35 },
            rotate:  { delay: IMPACT,               duration: 0.55, ease: [0.34, 1.56, 0.64, 1] },
            scale:   { delay: IMPACT,               duration: 0.75, times: [0, 0.01, 0.02, 0.22, 0.52, 0.76, 1] },
          }}
        >
          <path d={HEART_PATH} transform="translate(50, 84)" fill="#fb7185" fillOpacity={0.45} />
        </motion.svg>

        {/* Right heart */}
        <motion.svg
          viewBox="0 0 100 96"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          style={heartStyle}
          initial={{ x: SVG_SIZE * 0.95, opacity: 0, rotate: 0, scale: 1 }}
          animate={{
            x: 0,
            opacity: 1,
            rotate: -15,
            scale: [1, 1, 1, 1.22, 0.87, 1.06, 1],
          }}
          transition={{
            x:       { delay: APPROACH_DELAY + 0.04, duration: APPROACH_DUR, ease: [0.22, 1, 0.36, 1] },
            opacity: { delay: APPROACH_DELAY - 0.06,  duration: 0.35 },
            rotate:  { delay: IMPACT,                  duration: 0.55, ease: [0.34, 1.56, 0.64, 1] },
            scale:   { delay: IMPACT + 0.04,           duration: 0.75, times: [0, 0.01, 0.02, 0.22, 0.52, 0.76, 1] },
          }}
        >
          <path d={HEART_PATH} transform="translate(50, 84)" fill="#fb7185" fillOpacity={0.45} />
        </motion.svg>
      </div>

      {/* App wordmark */}
      <motion.div
        className="text-center"
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: IMPACT + 0.65, duration: 1.0, ease: [0.22, 1, 0.36, 1] }}
      >
        <h1 className="text-5xl font-serif text-rose-900/45 tracking-tighter">lover.</h1>
        <p className="text-[9px] text-stone-300 font-light tracking-[0.5em] uppercase mt-2">
          Two Hearts · One World
        </p>
      </motion.div>
    </motion.div>
  );
};

export default SplashScreen;
