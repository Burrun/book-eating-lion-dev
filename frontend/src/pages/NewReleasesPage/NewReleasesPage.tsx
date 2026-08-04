import BookCard from '../../components/BookCard/BookCard.js'
import { newReleases } from '../../data/mockBooks.js'

export default function NewReleasesPage() {
  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <h2 className="text-xl font-bold">✨ 신간</h2>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {newReleases.map((book) => (
            <BookCard key={book.id} {...book} />
          ))}
        </div>
      </section>
    </main>
  )
}
