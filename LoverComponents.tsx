import React, { useState } from 'react';
import { Heart, Calendar, Sparkles, MapPin, Clock, Image as ImageIcon, Mail, Lock, Unlock, Play, Plus, History } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import MemoryDetail, { Memory, MediaItem } from './MemoryDetail';

// 1. 相爱时光 (Gallery)

const mockMemories: Memory[] = [
  {
    id: '1',
    title: '第一次看海',
    date: '2025年6月15日',
    location: '三亚·大东海',
    description: '那天海风很大，你的头发一直乱，我帮你拢了一遍又一遍。后来太阳快落了，你突然拉着我跑向浪里，鞋子都湿透了，我们笑得完全停不下来。\n\n我第一次知道，原来海可以这么好看——是因为有你站在那里。',
    mood: '💛 温暖',
    coverImage: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800',
    tags: ['第一次', '海边', '日落'],
    media: [
      { id: 'm1', type: 'image', url: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800' },
      { id: 'm2', type: 'image', url: 'https://images.unsplash.com/photo-1519046904884-53103b34b206?w=800' },
      { id: 'm3', type: 'image', url: 'https://images.unsplash.com/photo-1473116763249-2faaef81ccda?w=800' },
      { id: 'm4', type: 'video', url: '#', thumbnail: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800' },
      { id: 'm5', type: 'image', url: 'https://images.unsplash.com/photo-1531306728370-e2ebd9d7bb99?w=800' },
    ],
  },
  {
    id: '2',
    title: '生日惊喜',
    date: '2026年2月20日',
    location: '我们家',
    description: '你以为我忘了，其实我准备了整整三周。看到你拆开蛋糕盒的表情，那一秒我就知道，所有的秘密都值得。',
    mood: '🌸 粉嫩',
    coverImage: 'https://images.unsplash.com/photo-1464349095431-e9a21285b5f3?w=800',
    tags: ['生日', '惊喜', '在一起'],
    media: [
      { id: 'm6', type: 'image', url: 'https://images.unsplash.com/photo-1464349095431-e9a21285b5f3?w=800' },
      { id: 'm7', type: 'video', url: '#', thumbnail: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800' },
      { id: 'm8', type: 'image', url: 'https://images.unsplash.com/photo-1587314168485-3236d6710814?w=800' },
    ],
  },
  {
    id: '3',
    title: '雨天的书店',
    date: '2025年11月3日',
    location: '方所·成都',
    description: '下雨就下雨吧，反正我们哪儿也不想去。',
    mood: '🩶 静谧',
    coverImage: 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=800',
    tags: ['雨天', '慢时光'],
    media: [
      { id: 'm9', type: 'image', url: 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=800' },
      { id: 'm10', type: 'image', url: 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=800' },
    ],
  },
  {
    id: '4',
    title: '山顶看日出',
    date: '2025年8月10日',
    location: '峨眉山·金顶',
    description: '凌晨三点出发，摸黑爬了两个多小时。你一直说腿酸，但从来没说要停。\n\n那一刻金光从云海里破出来，你没说话，我也没说话，就那样站着，觉得什么都值得。',
    mood: '🌅 壮阔',
    coverImage: 'https://images.unsplash.com/photo-1473116763249-2faaef81ccda?w=800',
    tags: ['日出', '爬山', '云海'],
    media: [
      { id: 'm11', type: 'image', url: 'https://images.unsplash.com/photo-1473116763249-2faaef81ccda?w=800' },
      { id: 'm12', type: 'image', url: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800' },
      { id: 'm13', type: 'video', url: '#', thumbnail: 'https://images.unsplash.com/photo-1531306728370-e2ebd9d7bb99?w=800' },
    ],
  },
  {
    id: '5',
    title: '第一顿自己做的饭',
    date: '2024年12月24日',
    location: '家',
    description: '平安夜，外面太冷，我们决定不出门。你负责切菜，我负责炒。其实我根本不会，但假装很会的样子。\n\n最后菜有点咸，但你说是你吃过最好吃的一顿。',
    mood: '🏠 居家',
    coverImage: 'https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=800',
    tags: ['圣诞', '做饭', '平安夜'],
    media: [
      { id: 'm14', type: 'image', url: 'https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=800' },
      { id: 'm15', type: 'image', url: 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800' },
    ],
  },
  {
    id: '6',
    title: '夜市与烟火',
    date: '2025年9月29日',
    location: '成都·锦里',
    description: '你买了一串糖葫芦，吃了一口，说太酸，然后把剩下的全塞给我。\n\n人很多，但我只记得你在灯火里回头找我的那一瞬间。',
    mood: '🔥 热闹',
    coverImage: 'https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=800',
    tags: ['夜市', '烟火气', '国庆'],
    media: [
      { id: 'm16', type: 'image', url: 'https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=800' },
      { id: 'm17', type: 'image', url: 'https://images.unsplash.com/photo-1529634806980-85c3dd6d34ac?w=800' },
      { id: 'm18', type: 'video', url: '#', thumbnail: 'https://images.unsplash.com/photo-1514190051997-0f6f39ca5cde?w=800' },
    ],
  },
  {
    id: '7',
    title: '咖啡馆的下午',
    date: '2026年1月11日',
    location: '隐居咖啡·北京',
    description: '你在看书，我在发呆看你。窗外在下雪，屋里很暖。\n\n这种普通到不能再普通的下午，我想一直都有。',
    mood: '☕ 慵懒',
    coverImage: 'https://images.unsplash.com/photo-1445116572660-236099ec97a0?w=800',
    tags: ['咖啡', '下雪天', '慢时光'],
    media: [
      { id: 'm19', type: 'image', url: 'https://images.unsplash.com/photo-1445116572660-236099ec97a0?w=800' },
      { id: 'm20', type: 'image', url: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=800' },
      { id: 'm21', type: 'image', url: 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800' },
    ],
  },
  {
    id: '8',
    title: '第一次旅行',
    date: '2025年2月14日',
    location: '大理·洱海',
    description: '我们骑着单车绕洱海，风很大，你一直喊冷，然后把脸埋进我背里。\n\n那条路很长，但我希望它再长一点。',
    mood: '🚲 自由',
    coverImage: 'https://images.unsplash.com/photo-1506929562872-bb421503ef21?w=800',
    tags: ['情人节', '大理', '骑行'],
    media: [
      { id: 'm22', type: 'image', url: 'https://images.unsplash.com/photo-1506929562872-bb421503ef21?w=800' },
      { id: 'm23', type: 'image', url: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800' },
      { id: 'm24', type: 'video', url: '#', thumbnail: 'https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?w=800' },
      { id: 'm25', type: 'image', url: 'https://images.unsplash.com/photo-1501854140801-50d01698950b?w=800' },
    ],
  },
  {
    id: '9',
    title: '深夜便利店',
    date: '2025年4月3日',
    location: '楼下全家',
    description: '凌晨两点，你说饿了。我们穿着睡衣下楼，买了关东煮和冰淇淋。\n\n坐在便利店门口的台阶上，一边吹风一边吃，没什么特别，却特别开心。',
    mood: '🌙 深夜',
    coverImage: 'https://images.unsplash.com/photo-1604719312566-8912e9227c6a?w=800',
    tags: ['深夜', '便利店', '小确幸'],
    media: [
      { id: 'm26', type: 'image', url: 'https://images.unsplash.com/photo-1604719312566-8912e9227c6a?w=800' },
      { id: 'm27', type: 'image', url: 'https://images.unsplash.com/photo-1528360983277-13d401cdc186?w=800' },
    ],
  },
  {
    id: '10',
    title: '相遇一千天',
    date: '2024年10月24日',
    location: '我们相遇的地方',
    description: '一千天前，你坐在我旁边，问我借了一支笔，还回来的时候笑了一下。\n\n就是那一下，我就完了。\n\n一千天了，我还是觉得遇见你是这辈子最好的事。',
    mood: '💕 珍贵',
    coverImage: 'https://images.unsplash.com/photo-1529634806980-85c3dd6d34ac?w=800',
    tags: ['纪念日', '一千天', '相遇'],
    media: [
      { id: 'm28', type: 'image', url: 'https://images.unsplash.com/photo-1529634806980-85c3dd6d34ac?w=800' },
      { id: 'm29', type: 'image', url: 'https://images.unsplash.com/photo-1516589174184-c68526574af8?w=800' },
      { id: 'm30', type: 'image', url: 'https://images.unsplash.com/photo-1494774157365-9e04c6720e47?w=800' },
      { id: 'm31', type: 'video', url: '#', thumbnail: 'https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=800' },
    ],
  },
];

// ── Gallery card variants ───────────────────────────────────────

// Variant A: single full cover + text overlay
const CardSingle: React.FC<{ memory: Memory; height: number; delay: number; onClick: () => void }> = ({ memory, height, delay, onClick }) => (
  <motion.div
    initial={{ opacity: 0, y: 14 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ delay }}
    onClick={onClick}
    className="relative rounded-[1.8rem] overflow-hidden shadow-sm cursor-pointer mb-3"
    style={{ height }}
  >
    <img src={memory.coverImage} className="w-full h-full object-cover" alt="" />
    <div className="absolute inset-0 bg-gradient-to-t from-black/65 via-black/5 to-transparent" />
    {memory.media.some(m => m.type === 'video') && (
      <div className="absolute top-3 left-3 w-6 h-6 bg-white/25 backdrop-blur-sm rounded-full flex items-center justify-center">
        <Play className="w-2.5 h-2.5 text-white fill-current ml-0.5" />
      </div>
    )}
    <div className="absolute inset-x-0 bottom-0 p-4">
      <p className="text-white text-xs font-serif leading-snug">{memory.title}</p>
      <p className="text-white/50 text-[8px] mt-0.5 font-light">{memory.date}</p>
    </div>
  </motion.div>
);

// Variant B: top cover (60%) + bottom strip of 2 mini thumbs (40%)
const CardStrip: React.FC<{ memory: Memory; height: number; delay: number; onClick: () => void }> = ({ memory, height, delay, onClick }) => {
  const thumbs = memory.media.slice(1, 3);
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay }}
      onClick={onClick}
      className="rounded-[1.8rem] overflow-hidden shadow-sm cursor-pointer mb-3 bg-white/50"
      style={{ height }}
    >
      {/* Cover — 62% */}
      <div className="relative" style={{ height: '62%' }}>
        <img src={memory.coverImage} className="w-full h-full object-cover" alt="" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/30 to-transparent" />
        <span className="absolute top-2.5 left-3 px-2 py-0.5 bg-white/25 backdrop-blur-sm rounded-full text-[7px] text-white tracking-widest">
          {memory.mood}
        </span>
      </div>
      {/* Strip — 38% */}
      <div className="flex gap-1.5 p-2" style={{ height: '38%' }}>
        {thumbs.length > 0 ? thumbs.map(item => (
          <div key={item.id} className="flex-1 rounded-xl overflow-hidden relative">
            <img src={item.thumbnail || item.url} className="w-full h-full object-cover" alt="" />
            {item.type === 'video' && (
              <div className="absolute inset-0 flex items-center justify-center bg-black/20">
                <Play className="w-2.5 h-2.5 text-white fill-current" />
              </div>
            )}
          </div>
        )) : null}
        {/* Placeholder if fewer than 2 extra media */}
        {thumbs.length < 2 && (
          <div className="flex-1 rounded-xl bg-rose-50/60 flex items-center justify-center">
            <ImageIcon className="w-3 h-3 text-rose-200" />
          </div>
        )}
        <div className="flex flex-col justify-end pb-0.5 pl-0.5 min-w-0">
          <p className="text-stone-600 text-[9px] font-serif leading-tight truncate">{memory.title}</p>
          <p className="text-stone-300 text-[7px] mt-0.5 font-light">{memory.date}</p>
        </div>
      </div>
    </motion.div>
  );
};

// Variant C: two images split top/bottom
const CardDuo: React.FC<{ memory: Memory; height: number; delay: number; onClick: () => void }> = ({ memory, height, delay, onClick }) => {
  const second = memory.media[1] ?? memory.media[0];
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay }}
      onClick={onClick}
      className="rounded-[1.8rem] overflow-hidden shadow-sm cursor-pointer mb-3 flex flex-col gap-1"
      style={{ height }}
    >
      <div className="relative flex-[3] rounded-t-[1.8rem] overflow-hidden">
        <img src={memory.coverImage} className="w-full h-full object-cover" alt="" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent" />
        <span className="absolute bottom-2 left-3 text-white text-[9px] font-serif">{memory.title}</span>
      </div>
      <div className="relative flex-[2] rounded-b-[1.8rem] overflow-hidden">
        <img src={second.thumbnail || second.url} className="w-full h-full object-cover" alt="" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
        <span className="absolute bottom-2 right-3 text-white/60 text-[7px] font-light">{memory.date}</span>
        {second.type === 'video' && (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-7 h-7 bg-white/25 backdrop-blur-sm rounded-full flex items-center justify-center">
              <Play className="w-2.5 h-2.5 text-white fill-current ml-0.5" />
            </div>
          </div>
        )}
      </div>
    </motion.div>
  );
};

// ── Gallery List ────────────────────────────────────────────────
export const LoveGallery: React.FC = () => {
  const [selected, setSelected] = useState<Memory | null>(null);

  // Assign card variant + height per item for visual rhythm
  // pattern: [variant, height]
  const cardConfigs: Array<['single' | 'strip' | 'duo', number]> = [
    ['strip', 210],
    ['single', 240],
    ['duo', 230],
    ['single', 180],
    ['strip', 220],
    ['duo', 250],
    ['single', 200],
    ['strip', 215],
    ['single', 190],
  ];

  const rest = mockMemories.slice(1);
  // Split into left / right columns, right column starts lower for offset
  const leftItems = rest.filter((_, i) => i % 2 === 0);
  const rightItems = rest.filter((_, i) => i % 2 === 1);

  const renderCard = (memory: Memory, globalIdx: number) => {
    const [variant, height] = cardConfigs[globalIdx % cardConfigs.length];
    const delay = 0.06 + globalIdx * 0.05;
    const props = { memory, height, delay, onClick: () => setSelected(memory) };
    if (variant === 'strip') return <CardStrip key={memory.id} {...props} />;
    if (variant === 'duo') return <CardDuo key={memory.id} {...props} />;
    return <CardSingle key={memory.id} {...props} />;
  };

  return (
    <>
      <AnimatePresence>
        {selected && (
          <MemoryDetail memory={selected} onBack={() => setSelected(null)} />
        )}
      </AnimatePresence>

      <div className="p-4 pb-32 space-y-5">
        {/* Header */}
        <div className="flex justify-between items-end px-2">
          <div>
            <h2 className="text-2xl font-serif text-rose-900/80">相爱时光</h2>
            <p className="text-[10px] text-stone-400 font-light tracking-widest uppercase">Visual Memories</p>
          </div>
          <button className="w-10 h-10 bg-rose-50 rounded-full flex items-center justify-center text-rose-400">
            <Plus className="w-5 h-5" />
          </button>
        </div>

        {/* Featured — full width, cover + media strip */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.04 }}
          onClick={() => setSelected(mockMemories[0])}
          className="cursor-pointer rounded-[2.5rem] overflow-hidden shadow-md"
          style={{ height: 300 }}
        >
          {/* Top 65%: cover */}
          <div className="relative" style={{ height: '65%' }}>
            <img src={mockMemories[0].coverImage} className="w-full h-full object-cover" alt="" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent" />
            <div className="absolute inset-x-0 bottom-0 px-5 pb-3">
              <span className="inline-block px-2 py-0.5 bg-white/20 backdrop-blur-sm rounded-full text-[7px] text-white tracking-widest uppercase mb-1">
                {mockMemories[0].mood}
              </span>
              <h3 className="text-lg font-serif text-white leading-tight">{mockMemories[0].title}</h3>
            </div>
          </div>
          {/* Bottom 35%: media strip */}
          <div className="flex gap-1.5 p-2 bg-white/60 backdrop-blur-sm" style={{ height: '35%' }}>
            {mockMemories[0].media.slice(0, 4).map((item, i) => (
              <div key={item.id} className={`relative rounded-xl overflow-hidden flex-1 ${i === 0 ? 'flex-[1.5]' : ''}`}>
                <img src={item.thumbnail || item.url} className="w-full h-full object-cover" alt="" />
                {item.type === 'video' && (
                  <div className="absolute inset-0 bg-black/20 flex items-center justify-center">
                    <Play className="w-3 h-3 text-white fill-current" />
                  </div>
                )}
              </div>
            ))}
            {mockMemories[0].media.length > 4 && (
              <div className="relative rounded-xl overflow-hidden flex-1">
                <img src={mockMemories[0].media[4].url} className="w-full h-full object-cover opacity-50" alt="" />
                <div className="absolute inset-0 bg-black/30 flex items-center justify-center">
                  <span className="text-white text-xs font-light">+{mockMemories[0].media.length - 4}</span>
                </div>
              </div>
            )}
            <div className="flex flex-col justify-center pl-1 min-w-0">
              <div className="flex items-center gap-1 text-stone-400">
                <MapPin className="w-2.5 h-2.5 flex-shrink-0" />
                <span className="text-[7px] font-light truncate">{mockMemories[0].location}</span>
              </div>
              <p className="text-stone-300 text-[7px] mt-1 font-light">{mockMemories[0].date}</p>
            </div>
          </div>
        </motion.div>

        {/* Two-column freeform grid */}
        <div className="flex gap-3 items-start">
          {/* Left column */}
          <div className="flex-1">
            {leftItems.map((m, i) => renderCard(m, i * 2))}
          </div>
          {/* Right column — offset downward for stagger */}
          <div className="flex-1 mt-10">
            {rightItems.map((m, i) => renderCard(m, i * 2 + 1))}
          </div>
        </div>
      </div>
    </>
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
        <LoverLogo className="w-12 h-12 text-rose-300" />
      </motion.div>

      <p className="text-stone-400 text-xs font-light tracking-[0.3em] uppercase mb-2">Loving Journey</p>
      <div className="flex items-baseline gap-2">
        <span className="text-7xl font-serif text-rose-900/80 leading-none">{days}</span>
        <span className="text-stone-300 font-light tracking-widest">天</span>
      </div>
    </div>
  );
};

// 5. App Logo - Two hearts tilted toward each other, tips meeting at bottom center
const HEART_PATH = "M 0 0 C -10 -18, -34 -18, -34 -38 C -34 -58, -17 -66, 0 -50 C 17 -66, 34 -58, 34 -38 C 34 -18, 10 -18, 0 0 Z";

export const LoverLogo: React.FC<{ className?: string }> = ({ className }) => {
  return (
    <svg
      viewBox="0 0 100 96"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
    >
      <motion.path
        d={HEART_PATH}
        fill="currentColor"
        fillOpacity={0.45}
        transform="translate(50, 84) rotate(15)"
        initial={{ rotate: 50, opacity: 0, y: 10 }}
        animate={{ rotate: 15, opacity: 1, y: 0 }}
        style={{ transformOrigin: "50px 84px" }}
        transition={{ duration: 0.9, ease: [0.34, 1.56, 0.64, 1] }}
      />
      <motion.path
        d={HEART_PATH}
        fill="currentColor"
        fillOpacity={0.45}
        transform="translate(50, 84) rotate(-15)"
        initial={{ rotate: -50, opacity: 0, y: 10 }}
        animate={{ rotate: -15, opacity: 1, y: 0 }}
        style={{ transformOrigin: "50px 84px" }}
        transition={{ duration: 0.9, ease: [0.34, 1.56, 0.64, 1], delay: 0.1 }}
      />
    </svg>
  );
};
