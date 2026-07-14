import React, { useState } from 'react';
import { Heart, User, Plus, Image as ImageIcon, Mail, CalendarHeart, Sparkles, MessageCircleHeart } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { LoveGallery, AnniversaryWall, LetterBox, Milestone } from './components/LoverComponents';

type Tab = 'home' | 'gallery' | 'anniversaries' | 'letters' | 'profile';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('home');

  const tabs = [
    { id: 'home', icon: Heart, label: '空间' },
    { id: 'gallery', icon: ImageIcon, label: '时光' },
    { id: 'anniversaries', icon: CalendarHeart, label: '纪念' },
    { id: 'letters', icon: Mail, label: '信封' },
    { id: 'profile', icon: User, label: '我们' },
  ];

  const renderContent = () => {
    switch (activeTab) {
      case 'home':
        return (
          <motion.div 
            initial={{ opacity: 0, y: 10 }} 
            animate={{ opacity: 1, y: 0 }} 
            className="space-y-8 pb-32"
          >
            <div className="flex flex-col items-center pt-12 pb-4">
              <h1 className="text-4xl font-serif text-rose-900/60 mb-2">lover.</h1>
              <p className="text-[9px] text-stone-300 font-light tracking-[0.4em] uppercase">Two Hearts One World</p>
            </div>
            
            <Milestone />

            <div className="grid grid-cols-2 gap-4">
              <button className="h-40 bg-white/60 backdrop-blur-sm rounded-[3rem] border border-rose-50 flex flex-col items-center justify-center gap-3 shadow-sm active:scale-95 transition-all">
                <div className="w-12 h-12 bg-rose-50 rounded-full flex items-center justify-center">
                  <MessageCircleHeart className="w-5 h-5 text-rose-300" />
                </div>
                <div className="text-center">
                  <span className="block text-xs text-stone-600 font-medium">写情书</span>
                  <span className="text-[9px] text-stone-400 font-light uppercase mt-1">Write Love</span>
                </div>
              </button>
              <button className="h-40 bg-white/60 backdrop-blur-sm rounded-[3rem] border border-rose-50 flex flex-col items-center justify-center gap-3 shadow-sm active:scale-95 transition-all">
                <div className="w-12 h-12 bg-orange-50 rounded-full flex items-center justify-center">
                  <Sparkles className="w-5 h-5 text-orange-200" />
                </div>
                <div className="text-center">
                  <span className="block text-xs text-stone-600 font-medium">存瞬间</span>
                  <span className="text-[9px] text-stone-400 font-light uppercase mt-1">Capture Now</span>
                </div>
              </button>
            </div>

            <div className="bg-white/40 p-8 rounded-[3rem] border border-rose-50/30">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-sm font-serif text-rose-900/50 uppercase tracking-widest">近期掠影</h3>
                <Sparkles className="w-4 h-4 text-rose-200" />
              </div>
              <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-hide">
                {[1, 2, 3].map(i => (
                  <div key={i} className="min-w-[140px] aspect-[4/5] rounded-3xl bg-stone-50 border border-white flex-shrink-0 relative overflow-hidden group">
                     <div className="absolute inset-0 bg-stone-100 animate-pulse group-hover:scale-110 transition-transform duration-700" />
                     <div className="absolute inset-0 bg-gradient-to-t from-black/5 to-transparent" />
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        );
      case 'gallery':
        return <LoveGallery />;
      case 'anniversaries':
        return <AnniversaryWall />;
      case 'letters':
        return <LetterBox />;
      case 'profile':
        return (
          <div className="flex flex-col items-center justify-center min-h-[75vh] space-y-10">
            <div className="relative">
              <div className="flex -space-x-6">
                <div className="w-24 h-24 rounded-full bg-rose-50 border-8 border-white shadow-xl flex items-center justify-center text-rose-300 font-serif text-2xl relative z-10">A</div>
                <div className="w-24 h-24 rounded-full bg-stone-50 border-8 border-white shadow-xl flex items-center justify-center text-stone-300 font-serif text-2xl relative z-0">B</div>
              </div>
              <div className="absolute -bottom-2 -right-2 w-10 h-10 bg-white rounded-full shadow-md flex items-center justify-center">
                 <Heart className="w-5 h-5 text-rose-400 fill-current opacity-20" />
              </div>
            </div>
            <div className="text-center space-y-2">
              <h2 className="text-2xl font-serif text-rose-900/70 tracking-tight">我们的小宇宙</h2>
              <p className="text-[10px] text-stone-400 font-light tracking-[0.5em] uppercase">Established 2024.10.24</p>
            </div>
            <div className="w-full space-y-4 px-4">
              {['空间装扮', '共同愿望单', '隐私控制', '关于Lover'].map(item => (
                <button key={item} className="w-full py-5 px-8 bg-white/60 backdrop-blur-md rounded-[2rem] border border-rose-50 flex justify-between items-center text-sm text-stone-500 font-light group hover:bg-rose-50/30 transition-all">
                  {item}
                  <div className="w-6 h-6 rounded-full border border-stone-100 flex items-center justify-center group-hover:border-rose-200 transition-colors">
                    <Plus className="w-3 h-3 text-stone-300 group-hover:text-rose-300 rotate-45" />
                  </div>
                </button>
              ))}
            </div>
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className="min-h-screen bg-[#fffcfb] text-stone-800 font-sans selection:bg-rose-100 relative">
      {/* Dynamic Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none z-0">
        <motion.div 
          animate={{ x: [0, 50, 0], y: [0, 30, 0] }}
          transition={{ duration: 20, repeat: Infinity, ease: "linear" }}
          className="absolute top-[-10%] right-[-10%] w-[70%] h-[70%] bg-rose-50/40 rounded-full blur-[120px]" 
        />
        <motion.div 
          animate={{ x: [0, -30, 0], y: [0, 50, 0] }}
          transition={{ duration: 25, repeat: Infinity, ease: "linear" }}
          className="absolute bottom-[0%] left-[-10%] w-[60%] h-[60%] bg-orange-50/30 rounded-full blur-[120px]" 
        />
      </div>

      <main className="max-w-md mx-auto min-h-screen px-6 relative z-10 overflow-x-hidden">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 1.02 }}
            transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
          >
            {renderContent()}
          </motion.div>
        </AnimatePresence>
      </main>

      {/* Modern Floating Action Button */}
      {['gallery', 'anniversaries', 'letters'].includes(activeTab) && (
        <motion.button
          initial={{ scale: 0, rotate: -90 }}
          animate={{ scale: 1, rotate: 0 }}
          whileHover={{ scale: 1.1 }}
          whileTap={{ scale: 0.9 }}
          className="fixed bottom-32 right-8 w-16 h-16 bg-rose-400 text-white rounded-[2rem] shadow-2xl shadow-rose-900/20 flex items-center justify-center z-50 transition-all"
        >
          <Plus className="w-7 h-7" />
        </motion.button>
      )}

      {/* Floating Island Navigation */}
      <nav className="fixed bottom-8 left-1/2 -translate-x-1/2 w-[92%] max-w-[420px] bg-white/70 backdrop-blur-2xl border border-white/50 rounded-[3rem] shadow-[0_20px_50px_rgba(0,0,0,0.05)] px-8 z-50">
        <div className="flex justify-between items-center h-20">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as Tab)}
              className={`relative flex flex-col items-center gap-1.5 transition-all duration-300 ${
                activeTab === tab.id ? 'text-rose-400 scale-110' : 'text-stone-300 hover:text-stone-400'
              }`}
            >
              <tab.icon className={`w-5 h-5 ${activeTab === tab.id ? 'fill-current opacity-20' : ''}`} />
              <span className="text-[9px] font-bold tracking-tight uppercase">{tab.label}</span>
              {activeTab === tab.id && (
                <motion.div 
                  layoutId="active-dot"
                  className="absolute -bottom-3 w-1.5 h-1.5 rounded-full bg-rose-400 shadow-[0_0_10px_rgba(251,113,133,0.5)]" 
                />
              )}
            </button>
          ))}
        </div>
      </nav>
    </div>
  );
}
