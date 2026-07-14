import React, { useState } from 'react';
import { Heart, Calendar, Sparkles, MapPin, Clock, Image as ImageIcon, Mail, Lock, Unlock, Play, Plus, History } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

// 1. 相爱时光 (Gallery) - 支持照片与视频展示
interface MediaItem {
  id: string;
  type: 'image' | 'video';
  url: string;
  thumbnail?: string;
  caption: string;
  date: string;
}

const mockMedia: MediaItem[] = [
  { id: '1', type: 'image', url: 'https://images.unsplash.com/photo-1516589174184-c68526574af8?w=800', caption: '第一次看海', date: '2025-06-15' },
  { id: '2', type: 'video', url: '#', thumbnail: 'https://images.unsplash.com/photo-1494774157365-9e04c6720e47?w=800', caption: '生日惊喜时刻', date: '2026-02-20' },
];

export const LoveGallery: React.FC = () => {
  return (
    <div className="space-y-6 p-4">
      <div className="flex justify-between items-end px-2">
        <div>
          <h2 className="text-2xl font-serif text-rose-900/80">相爱时光</h2>
          <p className="text-[10px] text-stone-400 font-light tracking-widest uppercase">Visual Memories</p>
        </div>
        <button className="w-10 h-10 bg-rose-50 rounded-full flex items-center justify-center text-rose-400">
          <Plus className="w-5 h-5" />
        </button>
      </div>
      <div className="grid grid-cols-2 gap-4">
        {mockMedia.map((item) => (
          <motion.div 
            key={item.id}
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="group relative aspect-[4/5] rounded-[2rem] overflow-hidden border border-rose-50/50 shadow-sm"
          >
            <img src={item.thumbnail || item.url} alt={item.caption} className="w-full h-full object-cover" />
            {item.type === 'video' && (
              <div className="absolute inset-0 flex items-center justify-center bg-black/10">
                <div className="w-10 h-10 bg-white/30 backdrop-blur-md rounded-full flex items-center justify-center">
                  <Play className="w-4 h-4 text-white fill-current ml-0.5" />
                </div>
              </div>
            )}
            <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent p-4">
              <p className="text-white text-[11px] font-light leading-tight">{item.caption}</p>
              <p className="text-white/60 text-[8px] mt-1">{item.date}</p>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
};

// 2. 爱的纪念日 (Anniversaries) - 融合照片视频回顾
interface Anniversary {
  id: string;
  title: string;
  date: string;
  image: string;
  type: 'milestone' | 'yearly';
  countdown: number;
}

const mockAnniversaries: Anniversary[] = [
  { id: '1', title: '相遇一千天', date: '2024-10-24', image: 'https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=400', type: 'milestone', countdown: 45 },
  { id: '2', title: '正式在一起', date: '2025-01-01', image: 'https://images.unsplash.com/photo-1529634806980-85c3dd6d34ac?w=400', type: 'yearly', countdown: 171 },
];

export const AnniversaryWall: React.FC = () => {
  return (
    <div className="space-y-6 p-4">
      <div className="px-2">
        <h2 className="text-2xl font-serif text-rose-900/80">爱的纪念日</h2>
        <p className="text-[10px] text-stone-400 font-light tracking-widest uppercase">Eternal Dates</p>
      </div>
      <div className="space-y-6">
        {mockAnniversaries.map((ann) => (
          <div key={ann.id} className="relative h-48 rounded-[2.5rem] overflow-hidden group shadow-md shadow-rose-900/5">
            <img src={ann.image} className="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" />
            <div className="absolute inset-0 bg-gradient-to-r from-black/40 via-transparent to-transparent" />
            <div className="absolute inset-0 p-8 flex flex-col justify-between">
              <div>
                <span className="px-3 py-1 bg-white/20 backdrop-blur-md rounded-full text-[9px] text-white uppercase tracking-widest">
                  {ann.type === 'milestone' ? '里程碑' : '年度纪念'}
                </span>
                <h3 className="text-xl font-serif text-white mt-3">{ann.title}</h3>
                <p className="text-white/70 text-xs font-light">{ann.date}</p>
              </div>
              <div className="flex items-baseline gap-1 text-white">
                <span className="text-xs font-light">还有</span>
                <span className="text-3xl font-serif">{ann.countdown}</span>
                <span className="text-xs font-light">天</span>
              </div>
            </div>
          </div>
        ))}
        <button className="w-full h-16 border-2 border-dashed border-rose-100 rounded-[2rem] flex items-center justify-center gap-2 text-stone-400 hover:bg-rose-50/30 transition-colors">
          <Plus className="w-4 h-4" />
          <span className="text-xs font-light">添加新的纪念日</span>
        </button>
      </div>
    </div>
  );
};

// 3. 爱的信封 (Letters) - 即时查看与时间胶囊
interface Letter {
  id: string;
  sender: string;
  title: string;
  content: string;
  type: 'instant' | 'capsule';
  unlockDate?: string;
  isUnlocked: boolean;
  createDate: string;
}

const mockLetters: Letter[] = [
  { id: '1', sender: 'A', title: '给当下的你', content: '谢谢你出现在我的生命里，让平凡的日子也有了光。', type: 'instant', isUnlocked: true, createDate: '2026-07-10' },
  { id: '2', sender: 'B', title: '给未来的我们', content: '那时的我们应该已经在冰岛看极光了吧？希望爱意如初。', type: 'capsule', unlockDate: '2027-01-01', isUnlocked: false, createDate: '2026-05-20' },
];

export const LetterBox: React.FC = () => {
  return (
    <div className="space-y-6 p-4">
      <div className="flex justify-between items-end px-2">
        <div>
          <h2 className="text-2xl font-serif text-rose-900/80">爱的信封</h2>
          <p className="text-[10px] text-stone-400 font-light tracking-widest uppercase">Secret Letters</p>
        </div>
        <div className="flex gap-2">
           <button className="p-3 bg-stone-50 text-stone-400 rounded-full"><History className="w-4 h-4" /></button>
           <button className="p-3 bg-rose-400 text-white rounded-full shadow-lg shadow-rose-200"><Mail className="w-4 h-4" /></button>
        </div>
      </div>
      
      <div className="grid gap-4">
        {mockLetters.map((letter) => (
          <motion.div 
            key={letter.id}
            whileTap={{ scale: 0.98 }}
            className={`relative p-8 rounded-[2.5rem] border transition-all overflow-hidden ${
              letter.isUnlocked 
                ? 'bg-white border-rose-50 shadow-sm' 
                : 'bg-stone-50 border-stone-100'
            }`}
          >
            {/* Stamp Style Decor */}
            <div className="absolute top-6 right-6 w-12 h-12 border-2 border-dashed border-rose-100 rounded-lg flex items-center justify-center rotate-12 opacity-40">
              <Heart className="w-6 h-6 text-rose-200 fill-current" />
            </div>

            <div className="flex items-center gap-2 mb-4">
              {letter.type === 'instant' ? (
                <div className="px-2 py-0.5 bg-rose-50 rounded text-[8px] text-rose-400 uppercase tracking-tighter">Instant</div>
              ) : (
                <div className="px-2 py-0.5 bg-stone-200 rounded text-[8px] text-stone-500 uppercase tracking-tighter">Time Capsule</div>
              )}
              <span className="text-[10px] text-stone-300">{letter.createDate}</span>
            </div>

            <h3 className={`text-lg font-serif mb-2 ${letter.isUnlocked ? 'text-rose-900/70' : 'text-stone-400'}`}>
              {letter.title}
            </h3>

            {letter.isUnlocked ? (
              <p className="text-sm text-stone-600 font-light leading-relaxed line-clamp-3">
                {letter.content}
              </p>
            ) : (
              <div className="flex flex-col items-center py-4 space-y-2">
                <Lock className="w-6 h-6 text-stone-300" />
                <p className="text-[10px] text-stone-400 italic">预计解锁时间: {letter.unlockDate}</p>
              </div>
            )}

            <div className="mt-6 flex items-center justify-between border-t border-stone-50 pt-4">
              <div className="flex -space-x-1">
                <div className="w-5 h-5 rounded-full bg-rose-100 border border-white" />
                <div className="w-5 h-5 rounded-full bg-stone-100 border border-white" />
              </div>
              <span className="text-[10px] text-stone-300 tracking-widest uppercase">FROM {letter.sender}</span>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
};

// 4. Milestone Dashboard Item
export const Milestone: React.FC = () => {
  const days = 1314; 
  return (
    <div className="p-10 bg-white/60 backdrop-blur-xl rounded-[3.5rem] border border-white shadow-xl shadow-rose-900/5 flex flex-col items-center text-center relative overflow-hidden">
      {/* Decorative Orbs */}
      <div className="absolute top-[-20%] left-[-20%] w-40 h-40 bg-rose-50/50 rounded-full blur-3xl" />
      <div className="absolute bottom-[-20%] right-[-20%] w-40 h-40 bg-orange-50/50 rounded-full blur-3xl" />
      
      <motion.div 
        animate={{ scale: [1, 1.05, 1] }}
        transition={{ duration: 4, repeat: Infinity }}
        className="w-24 h-24 bg-rose-50/50 rounded-full flex items-center justify-center shadow-inner mb-6"
      >
        <Heart className="w-12 h-12 text-rose-300 fill-rose-100/50" />
      </motion.div>

      <p className="text-stone-400 text-xs font-light tracking-[0.3em] uppercase mb-2">Loving Journey</p>
      <div className="flex items-baseline gap-2">
        <span className="text-7xl font-serif text-rose-900/80 leading-none">{days}</span>
        <span className="text-stone-300 font-light tracking-widest">天</span>
      </div>
    </div>
  );
};
