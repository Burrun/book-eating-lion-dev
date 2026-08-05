import { useSearchParams } from 'react-router-dom'
import BookCard from '../../components/BookCard/BookCard.tsx'
import SwipeDeck from '../../components/SwipeDeck/SwipeDeck.tsx'
import { catalogBooks, swipeDeck, CATEGORIES } from '../../data/mockBooks.ts'

const PAGE_SIZE = 8

export default function ProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const category = searchParams.get('category') ?? ''
  const rawPage = Number(searchParams.get('page') ?? '0')
  const page = Number.isFinite(rawPage) && rawPage >= 0 ? rawPage : 0

  const filteredBooks = category ? catalogBooks.filter((b) => b.category === category) : catalogBooks
  const totalPages = Math.max(1, Math.ceil(filteredBooks.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages - 1)
  const visibleBooks = filteredBooks.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE)

  function handleCategorySelect(value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set('category', value)
    else next.delete('category')
    next.delete('page')
    setSearchParams(next)
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    if (nextPage > 0) next.set('page', String(nextPage))
    else next.delete('page')
    setSearchParams(next)
  }

  return (
    <>
      <div className="hidden lg:block fixed right-4 top-24 z-30 w-72">
        <SwipeDeck items={swipeDeck} />
      </div>

      <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
        <section className="flex flex-col gap-2 rounded-2xl border border-honey/40 bg-honey/15 p-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="font-semibold text-forest">
            🎁 정기 구독 시뮬레이션: 월 9,900원에 무제한 대여 &amp; 사자 먹이 2배 적립!
          </p>
          <button className="shrink-0 rounded-full bg-forest px-5 py-2.5 font-semibold text-paper transition hover:bg-forest-light">
            구독하기 &gt;
          </button>
        </section>

        <section id="categories" className="flex scroll-mt-28 flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
          <div className="flex flex-wrap gap-2">
            {['전체', ...CATEGORIES].map((c) => {
              const value = c === '전체' ? '' : c
              const active = category === value
              return (
                <button
                  key={c}
                  onClick={() => handleCategorySelect(value)}
                  className={`rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors ${
                    active
                      ? 'border-coral bg-coral text-white'
                      : 'border-forest/15 bg-paper text-forest hover:bg-forest/5'
                  }`}
                >
                  {c}
                </button>
              )
            })}
          </div>

          {visibleBooks.length === 0 ? (
            <p className="text-sm text-forest/60">해당 카테고리에 도서가 없습니다</p>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                {visibleBooks.map((book) => (
                  <BookCard key={book.id} {...book} />
                ))}
              </div>

              {totalPages > 1 && (
                <div className="mt-2 flex items-center justify-center gap-4 text-sm">
                  <button
                    onClick={() => goToPage(Math.max(0, currentPage - 1))}
                    disabled={currentPage <= 0}
                    className="rounded border border-forest/20 px-3 py-1.5 text-forest disabled:opacity-40"
                  >
                    이전
                  </button>
                  <span className="text-forest/60">
                    {currentPage + 1} / {totalPages} 페이지
                  </span>
                  <button
                    onClick={() => goToPage(Math.min(totalPages - 1, currentPage + 1))}
                    disabled={currentPage >= totalPages - 1}
                    className="rounded border border-forest/20 px-3 py-1.5 text-forest disabled:opacity-40"
                  >
                    다음
                  </button>
                </div>
              )}
            </>
          )}
        </section>
      </main>
    </>
  )
}
