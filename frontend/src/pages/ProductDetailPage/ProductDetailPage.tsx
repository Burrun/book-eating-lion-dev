import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { getBookById } from '../../data/mockBooks.ts'
import type { Review } from '../../types/book.ts'

// 구독(유료) 회원 여부. 백엔드 구독 API 연동 전까지 임시 상수로 둔다.
// true: 웹툰 요약 컷 / false: 줄거리 텍스트 + 구독 유도
const isPremiumUser = false

export default function ProductDetailPage() {
  const { id } = useParams()
  const book = getBookById(id)
  const [reviews, setReviews] = useState<Review[]>(book.reviews)
  const [draftRating, setDraftRating] = useState(5)
  const [draftText, setDraftText] = useState('')

  function handleSubmitReview() {
    if (!draftText.trim()) return
    setReviews((prev) => [
      {
        id: `local-${Date.now()}`,
        author: '나',
        rating: draftRating,
        date: new Date().toISOString().slice(0, 10),
        text: draftText.trim(),
      },
      ...prev,
    ])
    setDraftText('')
    setDraftRating(5)
  }

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="flex flex-col gap-6 rounded-2xl border border-forest/10 bg-white p-6 sm:flex-row">
        <div className="flex h-72 w-full shrink-0 items-center justify-center rounded-xl border border-forest/10 bg-paper text-6xl sm:w-56">
          📖
        </div>
        <div className="flex flex-1 flex-col gap-2">
          <h1 className="text-2xl font-bold">{book.title}</h1>
          <p className="text-sm text-forest/60">
            저자: {book.author} | 출판사: {book.publisher} | ISBN: {book.isbn}
          </p>
          <p className="text-lg font-semibold text-coral">{book.price.toLocaleString()}원</p>
          <p className="text-sm text-forest/60">
            ⭐ {book.rating}점 (리뷰 {book.reviewCount}개) | {book.shippingNote}
          </p>
          <div className="mt-3 flex flex-wrap gap-3">
            <button className="rounded-full bg-forest px-6 py-2.5 font-semibold text-paper transition hover:bg-forest-light">
              🛒 장바구니
            </button>
            <button className="rounded-full bg-honey/25 px-6 py-2.5 font-semibold text-forest transition hover:bg-honey/40">
              ❤️ 찜하기
            </button>
          </div>
        </div>
      </section>

      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        {isPremiumUser ? (
          <>
            <h2 className="text-xl font-bold">🎨 웹툰 요약 컷</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              {book.webtoonCuts.map((cut) => (
                <div key={cut.id} className="overflow-hidden rounded-xl border border-forest/10">
                  <div className="flex h-48 items-center justify-center bg-paper text-4xl">🖼️</div>
                  <p className="p-3 text-sm text-forest/70">{cut.caption}</p>
                </div>
              ))}
            </div>
          </>
        ) : (
          <>
            <h2 className="text-xl font-bold">📖 줄거리</h2>
            <p className="text-sm leading-relaxed text-forest/80">{book.synopsis}</p>
            <div className="flex flex-col gap-3 rounded-xl border border-honey/40 bg-honey/15 p-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm font-semibold text-forest">
                🎨 구독 회원이 되면 이 책의 웹툰 요약 컷을 볼 수 있어요
              </p>
              <button className="shrink-0 rounded-full bg-forest px-5 py-2 text-sm font-semibold text-paper transition hover:bg-forest-light">
                구독하기 &gt;
              </button>
            </div>
          </>
        )}
      </section>

      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <h2 className="text-xl font-bold">⭐ 한줄평 &amp; 리뷰 작성</h2>
        <div className="flex flex-col gap-3 rounded-xl border border-forest/10 p-4">
          <div className="flex gap-1 text-2xl">
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={star}
                type="button"
                onClick={() => setDraftRating(star)}
                aria-label={`${star}점`}
                className={star <= draftRating ? 'text-honey' : 'text-forest/20'}
              >
                ★
              </button>
            ))}
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <input
              type="text"
              value={draftText}
              onChange={(e) => setDraftText(e.target.value)}
              placeholder="한줄평을 입력하세요..."
              className="flex-1 rounded-lg border border-forest/20 px-4 py-2.5 outline-none focus:border-forest"
            />
            <button
              onClick={handleSubmitReview}
              className="shrink-0 rounded-lg bg-forest px-6 py-2.5 font-semibold text-paper transition hover:bg-forest-light"
            >
              등록하기
            </button>
          </div>
        </div>

        <div className="flex flex-col gap-3">
          {reviews.map((review) => (
            <div key={review.id} className="rounded-xl bg-paper p-4">
              <p className="text-sm font-semibold">
                {review.author} {'⭐'.repeat(review.rating)} | {review.date}
              </p>
              <p className="mt-1 text-sm text-forest/80">{review.text}</p>
            </div>
          ))}
        </div>
      </section>
    </main>
  )
}
