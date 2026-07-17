/**
 * HomePage — 空间页（首页）
 *
 * 包含：
 *   - Logo + 应用名称
 *   - 恋爱天数（Milestone）
 *   - 每日寄语（按日期每天自动轮换）
 *   - 近期掠影（8张照片，三行 bento 布局）
 *
 * 依赖：
 *   - motion/react（framer-motion）
 *   - lucide-react
 *   - Tailwind CSS
 *   - Google Fonts: Pinyon Script
 *
 * 数据接入说明：
 *   - dailyQuotes: 后端返回当日寄语，或前端按 Date 取模
 *   - recentSnapshots: 取最近 8 条时光记录的封面图 + 标题 + 日期
 *   - Milestone 的 days: 从后端获取恋爱开始日期后计算差值
 */

import React, { useMemo, useState, useEffect } from 'react';
import { Heart } from 'lucide-react';
import { motion } from 'motion/react';

// ── Types ─────────────────────────────────────────────────────────

export interface Snapshot {
  src: string;
  label: string;
  date: string;
}

export interface Quote {
  text: string;
  author?: string;
}

interface HomePageProps {
  /** 恋爱天数，由业务层计算后传入 */
  days?: number;
  /** 近期掠影图片，建议 8 条 */
  snapshots?: Snapshot[];
  /** 今日寄语，不传则从内置列表按日期自动选 */
  todayQuote?: Quote;
}

// ── Built-in quote pool（前端兜底，每日按索引轮换）──────────────

const QUOTE_POOL: Quote[] = [
  { text: '爱不是寻找一个完美的人，而是学会用完美的眼光，欣赏那个不完美的人。', author: 'Sam Keen' },
  { text: '你是我见过最好的事，也是我最不想失去的事。' },
  { text: '在你身边，我才明白什么叫做刚刚好。' },
  { text: '最美好的事情，是找到一个人，他让你微笑，也让你变成更好的自己。' },
  { text: 'The best thing to hold onto in life is each other.', author: 'Audrey Hepburn' },
  { text: '有些人一旦遇见，便一眼万年。' },
  { text: '爱情不需要轰轰烈烈，只需要长长久久。' },
  { text: 'You are my today and all of my tomorrows.', author: 'Leo Christopher' },
  { text: '平凡的日子，因为有你，每一天都值得被记住。' },
  { text: '我想和你虚度时光，比如看看落日，比如散散步。', author: '李宗盛' },
  { text: 'Whatever our souls are made of, his and mine are the same.', author: 'Emily Brontë' },
  { text: '世界上最遥远的距离，是我站在你面前，你却不知道我爱你。', author: '泰戈尔' },
  { text: '爱一个人，就是在他疲惫的时候给他力量，在他失落的时候给他温暖。' },
  { text: 'I would rather spend one lifetime with you, than face all the ages of this world alone.', author: 'Tolkien' },
];

// ── Default snapshots（开发占位，接入真实数据后替换）─────────────

const DEFAULT_SNAPSHOTS: Snapshot[] = [
  { src: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600', label: '第一次看海', date: '2025.06.15' },
  { src: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?w=400', label: '日落时分', date: '2025.06.15' },
  { src: 'https://images.unsplash.com/photo-1464349095431-e9a21285b5f3?w=400', label: '生日惊喜', date: '2026.02.20' },
  { src: 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=400', label: '雨天书店', date: '2025.11.03' },
  { src: 'https://images.unsplash.com/photo-1473116763249-2faaef81ccda?w=400', label: '山顶的风', date: '2025.08.10' },
  { src: 'https://images.unsplash.com/photo-1531306728370-e2ebd9d7bb99?w=400', label: '黄昏海岸', date: '2025.06.16' },
  { src: 'https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=400', label: '相遇一千天', date: '2024.10.24' },
  { src: 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=400', label: '书页与咖啡', date: '2025.11.03' },
];

// ── Logo SVG（内联，无需额外文件）────────────────────────────────

const HEART_PATH = 'M 0 0 C -10 -18, -34 -18, -34 -38 C -34 -58, -17 -66, 0 -50 C 17 -66, 34 -58, 34 -38 C 34 -18, 10 -18, 0 0 Z';

const LoverLogoInline: React.FC<{ className?: string }> = ({ className }) => (
  <svg viewBox="0 0 100 96" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    <path d={HEART_PATH} fill="currentColor" fillOpacity={0.45} transform="translate(50, 84) rotate(15)" />
    <path d={HEART_PATH} fill="currentColor" fillOpacity={0.45} transform="translate(50, 84) rotate(-15)" />
  </svg>
);

// ── Milestone card ────────────────────────────────────────────────

const MilestoneCard: React.FC<{ days: number }> = ({ days }) => (
  <div className="p-10 bg-white/60 backdrop-blur-xl rounded-[3.5rem] border border-white shadow-xl shadow-rose-900/5 flex flex-col items-center text-center relative overflow-hidden">
    <div className="absolute top-[-20%] left-[-20%] w-40 h-40 bg-rose-50/50 rounded-full blur-3xl" />
    <div className="absolute bottom-[-20%] right-[-20%] w-40 h-40 bg-orange-50/50 rounded-full blur-3xl" />
    <motion.div
      animate={{ scale: [1, 1.05, 1] }}
      transition={{ duration: 4, repeat: Infinity }}
      className="w-24 h-24 bg-rose-50/50 rounded-full flex items-center justify-center shadow-inner mb-6"
    >
      <LoverLogoInline className="w-12 h-12 text-rose-300" />
    </motion.div>
    <p className="text-stone-400 text-xs font-light tracking-[0.3em] uppercase mb-2">Loving Journey</p>
    <div className="flex items-baseline gap-2">
      <span className="text-7xl font-serif text-rose-900/80 leading-none">{days}</span>
      <span className="text-stone-300 font-light tracking-widest">天</span>
    </div>
  </div>
);

// ── Main Component ────────────────────────────────────────────────

const HomePage: React.FC<HomePageProps> = ({
  days = 1314,
  snapshots = DEFAULT_SNAPSHOTS,
  todayQuote,
}) => {
  const quote = useMemo(() => {
    if (todayQuote) return todayQuote;
    const idx = Math.floor(Date.now() / 86400000) % QUOTE_POOL.length;
    return QUOTE_POOL[idx];
  }, [todayQuote]);

  const [featuredIdx, setFeaturedIdx] = useState(0);
  const [fadeIn, setFadeIn] = useState(true);

  useEffect(() => {
    const timer = setInterval(() => {
      setFadeIn(false);
      setTimeout(() => {
        setFeaturedIdx(prev => (prev + 1) % snapshots.length);
        setFadeIn(true);
      }, 400);
    }, 3500);
    return () => clearInterval(timer);
  }, [snapshots.length]);

  const featured = snapshots[featuredIdx];
  const s = snapshots;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-8 pb-32"
    >
      {/* ── Logo + 应用名 ─────────────────────────────────────── */}
      <div className="flex flex-col items-center pt-12 pb-2">
        <LoverLogoInline className="w-16 h-16 text-rose-400 mb-2" />
        <h1
          className="text-rose-900/50 mb-1"
          style={{ fontFamily: "'Pinyon Script', cursive", fontSize: '3rem', lineHeight: 1.1 }}
        >
          Lover.
        </h1>
        <p className="text-[9px] text-stone-300 font-light tracking-[0.4em] uppercase">
          Two Hearts One World
        </p>
      </div>

      {/* ── 恋爱天数 ──────────────────────────────────────────── */}
      <MilestoneCard days={days} />

      {/* ── 每日寄语 ──────────────────────────────────────────── */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.15 }}
        className="relative rounded-[2.5rem] overflow-hidden bg-gradient-to-br from-rose-50 via-white to-orange-50/40 border border-rose-100/60 p-7 shadow-sm"
      >
        {/* 装饰引号水印 */}
        <div
          className="absolute top-5 right-6 opacity-10 select-none"
          style={{ fontSize: 72, lineHeight: 1, fontFamily: 'serif' }}
        >
          "
        </div>
        <p className="text-[9px] text-rose-300 tracking-[0.5em] uppercase font-light mb-4">
          每日寄语
        </p>
        <p className="text-stone-500 text-sm font-light leading-[1.85] pr-6">
          {quote.text}
        </p>
        {quote.author && (
          <p className="text-[9px] text-stone-300 font-light tracking-widest mt-4">
            — {quote.author}
          </p>
        )}
        <div className="absolute bottom-5 right-6 w-5 h-5">
          <Heart className="w-full h-full text-rose-200 fill-current opacity-60" />
        </div>
      </motion.div>

      {/* ── 近期掠影 ──────────────────────────────────────────── */}
      <div>
        <div className="flex justify-between items-center mb-4 px-1">
          <h3 className="text-sm font-serif text-rose-900/50 uppercase tracking-widest">
            近期掠影
          </h3>
          <span className="text-[9px] text-stone-300 tracking-widest font-light uppercase">
            查看全部 →
          </span>
        </div>

        {/*
          布局说明：
          Row 1 (h=230): 左侧大图占 2/3 宽 × 全高，右侧两张小图上下各占 50%
          Row 2 (h=110): 三张等宽图并列
          Row 3 (h=100): 一张小图(1/3) + 一张宽图(2/3)
        */}

        {/* Row 1 */}
        <div className="grid grid-cols-3 gap-2.5 mb-2.5" style={{ height: 230 }}>
          {/* Featured carousel — cycles through all snapshots */}
          <div className="col-span-2 row-span-2 rounded-[2rem] overflow-hidden relative shadow-sm bg-stone-100">
            <img
              key={featuredIdx}
              src={featured?.src}
              className="w-full h-full object-cover"
              alt=""
              style={{
                transition: 'opacity 0.4s ease',
                opacity: fadeIn ? 1 : 0,
              }}
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/55 via-transparent to-transparent" />
            <div className="absolute bottom-0 inset-x-0 p-4">
              <p
                className="text-white text-xs font-serif"
                style={{ transition: 'opacity 0.4s ease', opacity: fadeIn ? 1 : 0 }}
              >
                {featured?.label}
              </p>
              <p
                className="text-white/50 text-[8px] mt-0.5"
                style={{ transition: 'opacity 0.4s ease', opacity: fadeIn ? 1 : 0 }}
              >
                {featured?.date}
              </p>
            </div>
            {/* Dot indicators */}
            <div className="absolute top-3 right-3 flex gap-1">
              {snapshots.map((_, i) => (
                <button
                  key={i}
                  onClick={() => { setFadeIn(false); setTimeout(() => { setFeaturedIdx(i); setFadeIn(true); }, 400); }}
                  className="rounded-full transition-all duration-300"
                  style={{
                    width: i === featuredIdx ? 14 : 5,
                    height: 5,
                    background: i === featuredIdx ? 'rgba(255,255,255,0.9)' : 'rgba(255,255,255,0.4)',
                  }}
                />
              ))}
            </div>
          </div>
          <div className="rounded-[1.5rem] overflow-hidden relative shadow-sm">
            <img src={s[1]?.src} className="w-full h-full object-cover" alt="" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
            <p className="absolute bottom-2 left-2.5 text-white text-[8px] font-light">{s[1]?.label}</p>
          </div>
          <div className="rounded-[1.5rem] overflow-hidden relative shadow-sm">
            <img src={s[2]?.src} className="w-full h-full object-cover" alt="" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
            <p className="absolute bottom-2 left-2.5 text-white text-[8px] font-light">{s[2]?.label}</p>
          </div>
        </div>

        {/* Row 2 */}
        <div className="grid grid-cols-3 gap-2.5 mb-2.5" style={{ height: 110 }}>
          {[s[3], s[4], s[5]].map((snap, i) => (
            <div key={i} className="rounded-[1.5rem] overflow-hidden relative shadow-sm">
              <img src={snap?.src} className="w-full h-full object-cover" alt="" />
              <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
              <p className="absolute bottom-2 left-2.5 text-white text-[8px] font-light leading-tight">
                {snap?.label}
              </p>
            </div>
          ))}
        </div>

        {/* Row 3 */}
        <div className="grid grid-cols-3 gap-2.5" style={{ height: 100 }}>
          <div className="rounded-[1.5rem] overflow-hidden relative shadow-sm">
            <img src={s[6]?.src} className="w-full h-full object-cover" alt="" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
            <p className="absolute bottom-2 left-2.5 text-white text-[8px] font-light">{s[6]?.label}</p>
          </div>
          <div className="col-span-2 rounded-[1.5rem] overflow-hidden relative shadow-sm">
            <img src={s[7]?.src} className="w-full h-full object-cover" alt="" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
            <p className="absolute bottom-2 left-2.5 text-white text-[8px] font-light">{s[7]?.label}</p>
          </div>
        </div>
      </div>
    </motion.div>
  );
};

export default HomePage;
