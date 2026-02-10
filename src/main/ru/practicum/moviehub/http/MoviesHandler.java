package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore moviesStore;
    private final Gson gson;
    private final int currentYear;
    private final Type movieListType;

    // Публичная константа пути
    public static final String MOVIES_PATH = "/movies";

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
        this.gson = new GsonBuilder().create();
        this.currentYear = Year.now().getValue();
        this.movieListType = new TypeToken<List<Movie>>() {
        }.getType();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();

            // Проверяем, что путь начинается с /movies
            if (!path.startsWith(MOVIES_PATH)) {
                sendError(exchange, 404, "Ресурс не найден");
                return;
            }

            switch (method) {
                case "GET":
                    handleGet(exchange, path, query);
                    break;
                case "POST":
                    handlePost(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange, path);
                    break;
                default:
                    sendError(exchange, 405, "Метод не поддерживается");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGet(HttpExchange exchange, String path, String query) throws IOException {
        // Обработка GET /movies
        if (path.equals(MOVIES_PATH)) {
            if (query != null && query.contains("year=")) {
                handleGetByYear(exchange, query);
            } else {
                handleGetAllMovies(exchange);
            }
            return;
        }

        // Обработка GET /movies/{id}
        if (path.startsWith(MOVIES_PATH + "/")) {
            handleGetMovieById(exchange, path);
            return;
        }

        // Если сюда дошли, значит путь не соответствует ни одному из паттернов
        sendError(exchange, 404, "Ресурс не найден");
    }

    private void handleGetAllMovies(HttpExchange exchange) throws IOException {
        List<Movie> movies = moviesStore.getAllMovies();
        String json = gson.toJson(movies, movieListType);
        sendJson(exchange, 200, json);
    }

    private void handleGetMovieById(HttpExchange exchange, String path) throws IOException {
        try {
            String[] parts = path.split("/");

            // Проверяем, что путь имеет правильный формат: /movies/{id}
            if (parts.length != 3) {
                sendError(exchange, 404, "Ресурс не найден");
                return;
            }

            String idStr = parts[2];

            // Пробуем преобразовать в Long
            Long id = Long.parseLong(idStr);

            Movie movie = moviesStore.getMovieById(id);
            if (movie == null) {
                sendError(exchange, 404, "Фильм не найден");
                return;
            }

            sendJson(exchange, 200, gson.toJson(movie));
        } catch (NumberFormatException e) {
            // Если id не число (например, "abc")
            sendError(exchange, 400, "Некорректный ID");
        }
    }

    private void handleGetByYear(HttpExchange exchange, String query) throws IOException {
        String yearParam = query.split("year=")[1].split("&")[0];

        try {
            int year = Integer.parseInt(yearParam);
            List<Movie> movies = moviesStore.getMoviesByYear(year);
            String json = gson.toJson(movies, movieListType);
            sendJson(exchange, 200, json);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Некорректный параметр запроса - 'year'");
        } catch (ArrayIndexOutOfBoundsException e) {
            sendError(exchange, 400, "Некорректный параметр запроса - 'year'");
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            sendError(exchange, 415, "Неподдерживаемый тип медиа");
            return;
        }

        try {
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }

            Movie movie = gson.fromJson(requestBody.toString(), Movie.class);

            List<String> errors = validateMovie(movie);
            if (!errors.isEmpty()) {
                ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации", errors);
                sendJson(exchange, 422, gson.toJson(errorResponse));
                return;
            }

            Movie savedMovie = moviesStore.addMovie(movie);

            exchange.getResponseHeaders().set("Content-Type", CT_JSON);
            exchange.sendResponseHeaders(201, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(gson.toJson(savedMovie).getBytes(StandardCharsets.UTF_8));
            }

        } catch (JsonSyntaxException e) {
            sendError(exchange, 400, "Некорректный JSON");
        }
    }

    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        try {
            String[] parts = path.split("/");
            if (parts.length != 3) {
                sendError(exchange, 404, "Ресурс не найден");
                return;
            }

            String idStr = parts[2];
            Long id = Long.parseLong(idStr);

            boolean deleted = moviesStore.deleteMovie(id);
            if (!deleted) {
                sendError(exchange, 404, "Фильм не найден");
                return;
            }

            sendNoContent(exchange);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Некорректный ID");
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie == null) {
            errors.add("Тело запроса не может быть пустым");
            return errors;
        }

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }

        int minYear = 1888;
        int maxYear = currentYear + 1;
        if (movie.getYear() < minYear || movie.getYear() > maxYear) {
            errors.add(String.format("год должен быть между %d и %d", minYear, maxYear));
        }

        return errors;
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(message);
        sendJson(exchange, statusCode, gson.toJson(errorResponse));
    }
}