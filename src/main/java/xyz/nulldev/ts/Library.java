package xyz.nulldev.ts;

import eu.kanade.tachiyomi.data.database.models.*;
import org.jetbrains.annotations.NotNull;
import xyz.nulldev.ts.util.OptionalUtils;
import xyz.nulldev.ts.util.UnboxTherapy;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Project: TachiServer
 * Author: nulldev
 * Creation Date: 11/07/16
 *
 * (Mostly) Drop-in replacement for DatabaseHelper
 */
public class Library {
    private int lastIntId = 10;
    private long lastLongId = Integer.MAX_VALUE;
    private List<Manga> mangas = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private Map<Long, List<Chapter>> chapters = new HashMap<>();
    private Map<Long, List<Integer>> mangaCategories = new HashMap<>();
    private Map<Long, List<MangaSync>> mangasSync = new HashMap<>();
    private final AtomicReference<ReentrantLock> masterLock = new AtomicReference<>(null);

    public synchronized void copyFrom(Library library) {
        synchronized (library) {
            this.lastIntId = library.lastIntId;
            this.lastLongId = library.lastLongId;
            this.mangas = new ArrayList<>(library.mangas);
            this.categories = new ArrayList<>(library.categories);
            this.chapters = new HashMap<>(library.chapters);
            this.mangaCategories = new HashMap<>(library.mangaCategories);
            this.mangasSync = new HashMap<>(library.mangasSync);
        }
    }

    public Library() {}

    public Library(Library library) {
        copyFrom(library);
    }

    public synchronized List<Manga> getMangas() {
        return mangas;
    }

    public synchronized List<Manga> getFavoriteMangas() {
        return mangas.stream().filter(Manga::getFavorite).collect(Collectors.toList());
    }

    public synchronized List<Category> getCategories() {
        return categories;
    }

    public synchronized Map<Long, List<Chapter>> getChapters() {
        return chapters;
    }

    @NotNull
    public synchronized List<Chapter> getChapters(Manga manga) {
        List<Chapter> mChapters = chapters.get(manga.getId());
        if (mChapters == null) {
            mChapters = new ArrayList<>();
        }
        return mChapters;
    }

    public synchronized Chapter getChapter(long id) {
        return OptionalUtils.getOrNull(
                chapters.values()
                        .stream()
                        .flatMap(Collection::stream)
                        .filter(chapter -> Objects.equals(chapter.getId(), id))
                        .findFirst());
    }

    public synchronized Map<Long, List<MangaSync>> getMangasSync() {
        return mangasSync;
    }

    public synchronized List<MangaSync> getMangasSync(Manga manga) {
        List<MangaSync> mSync = mangasSync.get(manga.getId());
        if (mSync == null) {
            mSync = new ArrayList<>();
        }
        return mSync;
    }

    public synchronized Map<Long, List<Integer>> getMangaCategories() {
        return mangaCategories;
    }

    public synchronized List<Category> getCategoriesForManga(Manga manga) {
        List<Integer> mCategories = mangaCategories.get(manga.getId());
        if (mCategories == null) {
            mCategories = new ArrayList<>();
        }
        return mCategories
                .stream()
                .map(
                        integer -> {
                            for (Category category : categories) {
                                if (Objects.equals(category.getId(), integer)) {
                                    return category;
                                }
                            }
                            return null;
                        })
                .filter(category -> category != null)
                .collect(Collectors.toList());
    }

    public synchronized <T> boolean removeWithIdInt(
            T toFind, List<T> objects, Function<T, Integer> mapping) {
        boolean found = false;
        if (toFind != null) {
            int targetId = mapping.apply(toFind);
            Iterator<T> iterator = objects.iterator();
            while (iterator.hasNext()) {
                T next = iterator.next();
                if (next != null && Objects.equals(targetId, mapping.apply(next))) {
                    found = true;
                    iterator.remove();
                }
            }
        }
        return found;
    }

    public synchronized <T> boolean removeWithIdLong(
            T toFind, List<T> objects, Function<T, Long> mapping) {
        boolean found = false;
        if (toFind != null) {
            long targetId = mapping.apply(toFind);
            Iterator<T> iterator = objects.iterator();
            while (iterator.hasNext()) {
                T next = iterator.next();
                if (next != null && Objects.equals(targetId, mapping.apply(next))) {
                    found = true;
                    iterator.remove();
                }
            }
        }
        return found;
    }

    public synchronized int insertCategory(Category category) {
        boolean removed = removeWithIdInt(category, categories, new CategoryMapping());
        categories.add(category);
        return removed ? UnboxTherapy.unbox(category.getId()) : newIntId();
    }

    public synchronized void deleteOldMangasCategories(List<Manga> toDelete) {
        for (Manga manga : toDelete) {
            if (manga != null) mangaCategories.remove(manga.getId());
        }
    }

    public synchronized void insertMangasCategories(List<MangaCategory> toInsert) {
        for (MangaCategory mangaCategory : toInsert) {
            List<Integer> categories = mangaCategories.get(mangaCategory.getManga_id());
            if (categories == null) {
                categories = new ArrayList<>();
                mangaCategories.put(mangaCategory.getManga_id(), categories);
            }
            categories.add(mangaCategory.getCategory_id());
        }
    }

    public synchronized long insertManga(Manga manga) {
        boolean removed = removeWithIdLong(manga, mangas, new MangaMapping());
        mangas.add(manga);
        return removed ? UnboxTherapy.unbox(manga.getId()) : newLongId();
    }

    public synchronized void insertChapters(List<Chapter> toInsert) {
        for (Chapter chapter1 : toInsert) {
            chapter1.setId(insertChapter(chapter1));
        }
    }

    public synchronized long insertChapter(Chapter chapter) {
        List<Chapter> found = chapters.get(chapter.getManga_id());
        if (found == null) {
            found = new ArrayList<>();
            chapters.put(chapter.getManga_id(), found);
        }
        boolean removed = removeWithIdLong(chapter, found, new ChapterMapping());
        found.add(chapter);
        return removed ? UnboxTherapy.unbox(chapter.getId()) : newLongId();
    }

    public synchronized void insertMangasSync(List<MangaSync> mangaSyncs) {
        for (MangaSync mangaSync : mangaSyncs) {
            mangaSync.setId(insertMangaSync(mangaSync));
        }
    }

    public synchronized long insertMangaSync(MangaSync mangaSync) {
        List<MangaSync> found = mangasSync.get(mangaSync.getManga_id());
        if (found == null) {
            found = new ArrayList<>();
            mangasSync.put(mangaSync.getManga_id(), found);
        }
        boolean removed = removeWithIdLong(mangaSync, found, new MangasSyncMapping());
        found.add(mangaSync);
        return removed ? UnboxTherapy.unbox(mangaSync.getId()) : newLongId();
    }

    public synchronized Manga getManga(String url, int source) {
        return OptionalUtils.getOrNull(
                mangas.stream()
                        .filter(manga -> Objects.equals(manga.getUrl(), url))
                        .filter(manga -> manga.getSource() == source)
                        .findFirst());
    }

    public synchronized Manga getManga(long id) {
        return OptionalUtils.getOrNull(
                mangas.stream().filter(manga -> Objects.equals(manga.getId(), id)).findFirst());
    }

    public int newIntId() {
        return lastIntId++;
    }

    public long newLongId() {
        return lastLongId++;
    }

    public LibraryTransaction newTransaction() {
        return new LibraryTransaction();
    }

    public class LibraryTransaction {
        Library library = new Library(Library.this);

        public Library getLibrary() {
            return library;
        }

        public void apply() {
            Library.this.copyFrom(library);
        }
    }

    private class CategoryMapping implements Function<Category, Integer> {
        @Override
        public Integer apply(Category category) {
            return UnboxTherapy.unbox(category.getId());
        }
    }

    private class MangaMapping implements Function<Manga, Long> {
        @Override
        public Long apply(Manga manga) {
            return UnboxTherapy.unbox(manga.getId());
        }
    }

    private class ChapterMapping implements Function<Chapter, Long> {
        @Override
        public Long apply(Chapter chapter) {
            return UnboxTherapy.unbox(chapter.getId());
        }
    }

    private class MangasSyncMapping implements Function<MangaSync, Long> {
        @Override
        public Long apply(MangaSync mangaSync) {
            return UnboxTherapy.unbox(mangaSync.getId());
        }
    }

    public AtomicReference<ReentrantLock> getMasterLock() {
        return masterLock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Library library = (Library) o;

        if (lastIntId != library.lastIntId) return false;
        if (lastLongId != library.lastLongId) return false;
        if (mangas != null ? !mangas.equals(library.mangas) : library.mangas != null) return false;
        if (categories != null ? !categories.equals(library.categories) : library.categories != null) return false;
        if (chapters != null ? !chapters.equals(library.chapters) : library.chapters != null) return false;
        if (mangaCategories != null ? !mangaCategories.equals(library.mangaCategories) : library.mangaCategories != null)
            return false;
        if (mangasSync != null ? !mangasSync.equals(library.mangasSync) : library.mangasSync != null) return false;
        return masterLock != null ? masterLock.equals(library.masterLock) : library.masterLock == null;
    }

    @Override
    public int hashCode() {
        int result = lastIntId;
        result = 31 * result + (int) (lastLongId ^ (lastLongId >>> 32));
        result = 31 * result + (mangas != null ? mangas.hashCode() : 0);
        result = 31 * result + (categories != null ? categories.hashCode() : 0);
        result = 31 * result + (chapters != null ? chapters.hashCode() : 0);
        result = 31 * result + (mangaCategories != null ? mangaCategories.hashCode() : 0);
        result = 31 * result + (mangasSync != null ? mangasSync.hashCode() : 0);
        result = 31 * result + (masterLock != null ? masterLock.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Library{" +
                "lastIntId=" + lastIntId +
                ", lastLongId=" + lastLongId +
                ", mangas=" + mangas +
                ", categories=" + categories +
                ", chapters=" + chapters +
                ", mangaCategories=" + mangaCategories +
                ", mangasSync=" + mangasSync +
                ", masterLock=" + masterLock +
                '}';
    }
}
