import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { getBookById, type Review } from '../../data/mockBooks.js'

const isPurchaseVerified = false

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
          <p className="text-lg font-semibold text-coral">
            정가: {book.listPrice.toLocaleString()}원 &rarr; 판매가: {book.salePrice.toLocaleString()}원
          </p>
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
            <button className="rounded-full border border-forest/20 px-6 py-2.5 font-semibold text-forest transition hover:bg-paper">
              📦 중고 매물 등록
            </button>
          </div>
        </div>
      </section>

      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <h2 className="text-xl font-bold">🔒 구매 인증 사용자 전용 웹툰 요약 컷</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {book.webtoonCuts.map((cut) => (
            <div key={cut.id} className="relative overflow-hidden rounded-xl border border-forest/10">
              <div
                className={`flex h-48 items-center justify-center bg-paper text-4xl ${
                  isPurchaseVerified ? '' : 'blur-sm'
                }`}
              >
                🖼️
              </div>
              <p className={`p-3 text-sm text-forest/70 ${isPurchaseVerified ? '' : 'blur-sm'}`}>{cut.caption}</p>
              {!isPurchaseVerified && (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-forest/60 text-center text-paper">
                  <span className="text-2xl">🔒</span>
                  <p className="px-4 text-sm font-semibold">구매 인증 후 열람 가능</p>
                </div>
              )}
            </div>
          ))}
        </div>
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
