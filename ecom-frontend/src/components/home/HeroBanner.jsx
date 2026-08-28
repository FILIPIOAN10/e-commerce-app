
// Import Swiper React components
import { Swiper, SwiperSlide } from 'swiper/react';

import 'swiper/css';
import 'swiper/css/navigation';
import 'swiper/css/pagination';
import 'swiper/css/scrollbar';
import 'swiper/css/effect-fade';
import 'swiper/css/autoplay';

import { Autoplay, Pagination, EffectFade, Navigation } from 'swiper/modules';


import { bannerLists } from '../../utils';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useLanguage } from '../../context/LanguageContext';


// Each slide gets a ground with depth rather than one flat fill — a single
// saturated colour behind a product photo is what dated the old banner.
const grounds = [
    "bg-[linear-gradient(120deg,#e8a825_0%,#d98f1c_55%,#b9741a_100%)]",
    "bg-[linear-gradient(120deg,#cf3b34_0%,#b32f2f_55%,#8d2530_100%)]",
    "bg-[linear-gradient(120deg,#1d8f57_0%,#177a4d_55%,#125f42_100%)]",
];



const HeroBanner = () => {
    const { t } = useTranslation("home");
    const lang = useLanguage();
    return (
        <div className='py-2 rounded-md'>
            <Swiper
                grabCursor = {true}
                autoplay = {{
                    delay:4000,
                    disableOnInteraction:false,

                }}
                navigation
                modules={[Pagination, EffectFade, Navigation, Autoplay]}
                pagination={{clickable:true}}
                scrollbar = {{draggable:true}}
                slidesPerView={1}>
                    {bannerLists.map((item,i) => (
                        <SwiperSlide key={item.id}>
                            <div className={`carousel-item sm:h-125 h-96 ${grounds[i % grounds.length]}`}>
                                {/* A soft vignette from the text side keeps the copy legible
                                    over whatever photograph the slide carries. */}
                                <div className='absolute inset-0 bg-[radial-gradient(120%_100%_at_0%_50%,rgba(0,0,0,0.42),transparent_65%)]' />

                                <div className='relative flex h-full items-center'>
                                    <div className='hidden lg:flex w-1/2 items-center justify-center px-12'>
                                        <div className='max-w-md'>
                                            <span className='inline-block text-xs font-semibold uppercase tracking-[0.18em] text-white/75'>
                                                {item.title}
                                            </span>
                                            <h2 className='mt-3 text-5xl font-extrabold leading-[1.05] text-white'>
                                                {item.subtitle}
                                            </h2>
                                            <p className='mt-4 text-base leading-relaxed text-white/85'>
                                                {item.description}
                                            </p>
                                            <Link
                                                className='mt-8 inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3
                                                           text-sm font-semibold text-gray-900 transition
                                                           hover:bg-white/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white'
                                                to={`/${lang}/products`}>
                                                {t("shop")}
                                                <span aria-hidden="true">&rarr;</span>
                                            </Link>
                                        </div>
                                    </div>
                                    <div className='flex h-full w-full items-center justify-center p-8 lg:w-1/2 lg:p-12'>
                                        <img
                                            src={item.image}
                                            alt={item.title}
                                            className='max-h-full w-auto object-contain drop-shadow-[0_24px_48px_rgba(0,0,0,0.35)]'
                                        />
                                    </div>
                                </div>
                            </div>
                        </SwiperSlide>
                    ))}
            </Swiper>
        </div>
    );
}

export default HeroBanner;