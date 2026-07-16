/**
 * MemoryDetail — 时光详情页
 *
 * 使用方式：
 *   <MemoryDetail memory={selectedMemory} onBack={() => setSelected(null)} />
 *
 * 需要用 AnimatePresence 包裹以获得进出场动画：
 *   <AnimatePresence>
 *     {selected && <MemoryDetail memory={selected} onBack={...} />}
 *   </AnimatePresence>
 *
 * 依赖：
 *   - motion/react（framer-motion）
 *   - lucide-react
 *   - Tailwind CSS
 */

import React, { useState, useRef } from 'react';
import { Heart, Calendar, MapPin, Play, ArrowLeft, Share2, Bookmark } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

// ── Types ────────────────────────────────────────────────────────

export interface MediaItem {
  id: string;
  type: 'image' | 'video';
  /** 原始资源地址（图片直接用，视频用于播放） */
  url: string;
  /** 视频封面图，type=video 时必填 */
  thumbnail?: string;
  caption?: string;
}

export interface Memory {
  id: string;
  title: string;
  /** 展示用日期字符串，如"2025年6月15日" */
  date: string;
  location?: string;
  /** 正文描述，支持 \n 换行 */
  description: string;
  /** 心情标签，如"💛 温暖" */
  mood: string;
  /** 封面图 URL，通常与 media[0] 相同 */
  coverImage: string;
  media: MediaItem[];
  tags: string[];
}

// ── Sub-components ───────────────────────────────────────────────

const VideoOverlay: React.FC<{ size?: 'sm' | 'md' | 'lg' }> = ({ size = 'md' }) => {
  const dim = { sm: 'w-10 h-10', md: 'w-12 h-12', lg: 'w-14 h-14' }[size];
  const icon = { sm: 'w-3.5 h-3.5', md: 'w-4 h-4', lg: 'w-5 h-5' }[size];
  return (
    <div className="absolute inset-0 flex items-center justify-center bg-black/20">
      <div className={`${dim} bg-white/30 backdrop-blur-md rounded-full flex items-center justify-center shadow-lg`}>
        <Play className={`${icon} text-white fill-current ml-0.5`} />
      </div>
    </div>
  );
};

const MediaTile: React.FC<{
  item: MediaItem;
  className?: string;
  onClick: () => void;
  videoSize?: 'sm' | 'md' | 'lg';
}> = ({ item, className = '', onClick, videoSize = 'md' }) => (
  <motion.div
    className={`relative overflow-hidden cursor-pointer ${className}`}
    whileTap={{ scale: 0.97 }}
    onClick={onClick}
  >
    <img
      src={item.thumbnail || item.url}
      alt={item.caption ?? ''}
      className="w-full h-full object-cover"
    />
    {item.type === 'video' && <VideoOverlay size={videoSize} />}
  </motion.div>
);

// ── Main Component ───────────────────────────────────────────────

interface MemoryDetailProps {
  memory: Memory;
  onBack: () => void;
}

const MemoryDetail: React.FC<MemoryDetailProps> = ({ memory, onBack }) => {
  const [lightbox, setLightbox] = useState<MediaItem | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const [hero, ...rest] = memory.media;
  const gridPair = rest.slice(0, 2);
  const remaining = rest.slice(2);

  return (
    <motion.div
      className="fixed inset-0 bg-[#fffcfb] z-50 overflow-y-auto"
      initial={{ x: '100%' }}
      animate={{ x: 0 }}
      exit={{ x: '100%' }}
      transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
      ref={scrollRef}
    >

      {/* ── Hero 封面 ─────────────────────────────────────────── */}
      <div className="relative h-[62vh] overflow-hidden">
        <img
          src={memory.coverImage}
          alt={memory.title}
          className="w-full h-full object-cover"
        />
        {/* 双向渐变：顶部保护导航栏，底部融入页面底色 */}
        <div className="absolute inset-0 bg-gradient-to-b from-black/35 via-transparent to-black/50" />
        <div className="absolute inset-0 bg-gradient-to-t from-[#fffcfb] via-transparent to-transparent" />

        {/* 顶部导航栏 */}
        <div className="absolute top-0 inset-x-0 flex items-center justify-between px-5 pt-12 pb-4">
          <motion.button
            onClick={onBack}
            whileTap={{ scale: 0.88 }}
            className="w-10 h-10 bg-white/20 backdrop-blur-md rounded-full flex items-center justify-center border border-white/30"
          >
            <ArrowLeft className="w-4 h-4 text-white" />
          </motion.button>

          <div className="flex gap-2">
            <motion.button
              whileTap={{ scale: 0.88 }}
              className="w-10 h-10 bg-white/20 backdrop-blur-md rounded-full flex items-center justify-center border border-white/30"
            >
              <Bookmark className="w-4 h-4 text-white" />
            </motion.button>
            <motion.button
              whileTap={{ scale: 0.88 }}
              className="w-10 h-10 bg-white/20 backdrop-blur-md rounded-full flex items-center justify-center border border-white/30"
            >
              <Share2 className="w-4 h-4 text-white" />
            </motion.button>
          </div>
        </div>

        {/* 封面底部：心情 + 标题 */}
        <div className="absolute bottom-8 inset-x-0 px-6">
          <span className="inline-block px-3 py-1 mb-3 bg-white/20 backdrop-blur-md rounded-full text-[9px] text-white tracking-widest uppercase border border-white/20">
            {memory.mood}
          </span>
          <h1 className="text-3xl font-serif text-white tracking-tight leading-tight drop-shadow-sm">
            {memory.title}
          </h1>
        </div>
      </div>

      {/* ── 内容区 ────────────────────────────────────────────── */}
      <div className="px-5 pb-24 space-y-7 -mt-2">

        {/* 日期 & 地点 */}
        <div className="flex items-center gap-4 pt-1">
          <div className="flex items-center gap-1.5 text-stone-400">
            <Calendar className="w-3.5 h-3.5 flex-shrink-0" />
            <span className="text-xs font-light">{memory.date}</span>
          </div>
          {memory.location && (
            <div className="flex items-center gap-1.5 text-stone-400">
              <MapPin className="w-3.5 h-3.5 flex-shrink-0" />
              <span className="text-xs font-light">{memory.location}</span>
            </div>
          )}
        </div>

        {/* 话题标签 */}
        {memory.tags.length > 0 && (
          <div className="flex gap-2 flex-wrap">
            {memory.tags.map(tag => (
              <span
                key={tag}
                className="px-3 py-1 bg-rose-50 rounded-full text-[10px] text-rose-400 font-light tracking-wide"
              >
                # {tag}
              </span>
            ))}
          </div>
        )}

        {/* 文字描述 */}
        <div className="bg-white/60 backdrop-blur-sm rounded-[2rem] p-6 border border-rose-50 shadow-sm">
          <p className="text-stone-500 text-sm font-light leading-[1.9] whitespace-pre-line">
            {memory.description}
          </p>
          {/* 底部情感分割线 */}
          <div className="mt-5 pt-4 border-t border-rose-50/70 flex items-center gap-2">
            <div className="w-4 h-px bg-rose-200" />
            <span className="text-[9px] text-stone-300 tracking-[0.4em] uppercase font-light">
              只有我们知道
            </span>
            <div className="flex-1 h-px bg-rose-100/60" />
          </div>
        </div>

        {/* ── 媒体网格 ──────────────────────────────────────── */}
        <div>
          <p className="text-[9px] text-stone-300 tracking-[0.4em] uppercase font-light mb-4">
            {memory.media.length} 个瞬间
          </p>

          {/* 第 1 张：4:3 宽图 */}
          {hero && (
            <MediaTile
              item={hero}
              className="w-full aspect-[4/3] rounded-[2rem] mb-3 shadow-md shadow-rose-900/5"
              onClick={() => setLightbox(hero)}
              videoSize="lg"
            />
          )}

          {/* 第 2–3 张：1:1 双列 */}
          {gridPair.length > 0 && (
            <div className="grid grid-cols-2 gap-3 mb-3">
              {gridPair.map(item => (
                <MediaTile
                  key={item.id}
                  item={item}
                  className="aspect-square rounded-[1.5rem] shadow-sm"
                  onClick={() => setLightbox(item)}
                  videoSize="sm"
                />
              ))}
            </div>
          )}

          {/* 第 4 张起：16:9 / 4:3 交替 */}
          {remaining.map((item, i) => (
            <MediaTile
              key={item.id}
              item={item}
              className={`w-full rounded-[1.75rem] shadow-sm mb-3 ${
                i % 2 === 0 ? 'aspect-[16/9]' : 'aspect-[4/3]'
              }`}
              onClick={() => setLightbox(item)}
            />
          ))}
        </div>

        {/* 页脚情感落款 */}
        <div className="flex flex-col items-center gap-3 pt-2 pb-2">
          <div className="w-8 h-px bg-rose-100" />
          <Heart className="w-4 h-4 text-rose-200 fill-current" />
          <p className="text-[9px] text-stone-300 tracking-[0.5em] uppercase font-light">
            {memory.date} · 永久珍藏
          </p>
        </div>
      </div>

      {/* ── Lightbox 全屏预览 ─────────────────────────────────── */}
      <AnimatePresence>
        {lightbox && (
          <motion.div
            className="fixed inset-0 bg-black/92 z-[60] flex items-center justify-center px-4"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setLightbox(null)}
          >
            <motion.img
              src={lightbox.thumbnail || lightbox.url}
              alt={lightbox.caption ?? ''}
              className="max-w-full max-h-[90vh] object-contain rounded-2xl"
              initial={{ scale: 0.88, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.88, opacity: 0 }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            />
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
};

export default MemoryDetail;
