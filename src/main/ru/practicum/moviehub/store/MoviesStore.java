package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class MoviesStore {
    private final Map<Long, Movie> movies = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);


    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public Movie getMovieById(Long id) {
        return movies.get(id);
    }

    public Movie addMovie(Movie movie) {
        Long id = idCounter.getAndIncrement();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public boolean deleteMovie(Long id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .toList();
    }

    public void clear() {
        movies.clear();
        idCounter.set(1);
    }

    public boolean isEmpty() {
        return movies.isEmpty();
    }
}