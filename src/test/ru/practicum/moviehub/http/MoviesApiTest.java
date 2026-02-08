package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore moviesStore;
    private static final Gson gson = new GsonBuilder().create();
    private static final Type MOVIE_LIST_TYPE = new ListOfMoviesTypeToken().getType();

    @BeforeAll
    static void beforeAll() {
        moviesStore = new MoviesStore();
        server = new MoviesServer(moviesStore, 8080);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        moviesStore.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Order(1)
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        // Используем ListOfMoviesTypeToken из тестовой папки
        List<Movie> movies = gson.fromJson(resp.body(), MOVIE_LIST_TYPE);
        assertTrue(movies.isEmpty(), "Ожидается пустой список фильмов");
    }

    @Test
    @Order(2)
    void postMovie_withValidData_returnsCreatedMovie() throws Exception {
        String movieJson = "{\"title\": \"Интерстеллар\", \"year\": 2014}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");

        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue);

        Movie createdMovie = gson.fromJson(resp.body(), Movie.class);
        assertNotNull(createdMovie.getId());
        assertEquals("Интерстеллар", createdMovie.getTitle());
        assertEquals(2014, createdMovie.getYear());
    }

    @Test
    @Order(3)
    void getMovies_afterAdding_returnsMoviesList() throws Exception {
        // Сначала добавляем фильм
        String movieJson = "{\"title\": \"Начало\", \"year\": 2010}";

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        client.send(postReq, HttpResponse.BodyHandlers.ofString());

        // Затем получаем список
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        // Используем ListOfMoviesTypeToken
        List<Movie> movies = gson.fromJson(resp.body(), MOVIE_LIST_TYPE);

        assertEquals(1, movies.size());
        assertEquals("Начало", movies.get(0).getTitle());
        assertEquals(2010, movies.get(0).getYear());
    }

    @Test
    @Order(4)
    void getMovieById_withValidId_returnsMovie() throws Exception {
        // Добавляем фильм
        String movieJson = "{\"title\": \"Матрица\", \"year\": 1999}";

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        Long movieId = createdMovie.getId();

        // Получаем фильм по ID
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(getReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());

        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals("Матрица", movie.getTitle());
        assertEquals(1999, movie.getYear());
    }

    @Test
    @Order(5)
    void getMovieById_withInvalidId_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", error.getError());
    }

    @Test
    @Order(6)
    void postMovie_withEmptyTitle_returnsValidationError() throws Exception {
        String movieJson = "{\"title\": \"\", \"year\": 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("название не должно быть пустым"));
    }

    @Test
    @Order(7)
    void deleteMovie_withValidId_returnsNoContent() throws Exception {
        // Добавляем фильм
        String movieJson = "{\"title\": \"Фильм для удаления\", \"year\": 2020}";

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        Long movieId = createdMovie.getId();

        // Удаляем фильм
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, resp.statusCode());
    }

    @Test
    @Order(8)
    void getMoviesByYear_withValidYear_returnsFilteredMovies() throws Exception {
        // Добавляем фильмы разных годов
        addMovie("Фильм 2020", 2020);
        addMovie("Фильм 2021", 2021);
        addMovie("Еще фильм 2020", 2020);

        // Получаем фильмы 2020 года
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2020"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());

        // Используем ListOfMoviesTypeToken
        List<Movie> movies = gson.fromJson(resp.body(), MOVIE_LIST_TYPE);

        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 2020));
    }

    @Test
    @Order(9)
    void postMovie_withInvalidYear_returnsValidationError() throws Exception {
        String movieJson = "{\"title\": \"Некорректный год\", \"year\": 1800}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().get(0).contains("год должен быть между"));
    }

    @Test
    @Order(10)
    void postMovie_withTooLongTitle_returnsValidationError() throws Exception {
        String longTitle = "A".repeat(101);
        String movieJson = "{\"title\": \"" + longTitle + "\", \"year\": 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("название не должно превышать 100 символов"));
    }

    @Test
    @Order(11)
    void getMoviesByYear_withInvalidYearParam_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=invalid"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса - 'year'", error.getError());
    }

    @Test
    @Order(12)
    void postMovie_withWrongContentType_returnsUnsupportedMediaType() throws Exception {
        String movieJson = "{\"title\": \"Фильм\", \"year\": 2020}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(415, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Неподдерживаемый тип медиа", error.getError());
    }

    private void addMovie(String title, int year) throws IOException, InterruptedException {
        String movieJson = "{\"title\": \"" + title + "\", \"year\": " + year + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}