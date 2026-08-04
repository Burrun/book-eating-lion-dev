import { Link } from 'react-router-dom'

interface BookCardProps {
  id: string
  title: string
  price: number
  rating: number
}

export default function BookCard({ id, title, price, rating }: BookCardProps) {
  return (
    <Link
      to={`/books/${id}`}
      className="flex flex-col gap-2.5 rounded-xl border border-forest/10 bg-paper p-3.5 transition hover:-translate-y-1 hover:shadow-lg hover:shadow-forest/10"
    >
      <div className="flex aspect-[3/4] items-center justify-center rounded-lg border border-forest/10 bg-white text-4xl">
        📖
      </div>
      <p className="line-clamp-2 text-sm font-semibold leading-snug">{title}</p>
      <p className="text-sm text-forest/60">
        {price.toLocaleString()}원 | ⭐{rating}
      </p>
    </Link>
  )
}
