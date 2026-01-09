package com.guildhub.grpc_news_service.grpc;

import com.google.protobuf.Timestamp;
import com.guildhub.grpc_news_service.Entity.NewsEntity;
import com.guildhub.grpc_news_service.Repository.NewsRepository;
import com.guildhub.newsservice.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityNotFoundException;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@GrpcService
public class NewsGrpcService extends NewsServiceGrpc.NewsServiceImplBase {

    private final NewsRepository newsRepository;

    private final MinioService minioService;

    public NewsGrpcService(NewsRepository newsRepository, MinioService minioService) {
        this.newsRepository = newsRepository;
        this.minioService = minioService;
    }

    @Override
    public void createNews(CreateNewsRequest request, StreamObserver<CreateNewsResponse> responseObserver) {

        String title = request.getTitle();

        if(newsRepository.existsByTitle(title))
        {
            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription("News with title " + title + "already exist.")
                            .asRuntimeException()
            );
            return;
        }


        NewsEntity news = new NewsEntity();
        news.setTitle(title);
        news.setContent(request.getContent());
        news.setAuthor(request.getAuthor());
        if(!request.getImageData().isEmpty())
        {
            String imageUrl = minioService.uploadImage(
                    request.getImageData().toByteArray(),
                    request.getImageContentType(),
                    request.getImageFilename()
            );
            news.setImageUrl(imageUrl);
        }
        news.setIsNew(request.getIsNew());
        news.setCreatedAt(Instant.now());
        news.setUpdatedAt(Instant.now());

        NewsEntity savedNews = newsRepository.save(news);

        News proto = toProto(savedNews);

        CreateNewsResponse response = CreateNewsResponse.newBuilder()
                .setNews(proto)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getLatestNews(GetLatestNewsRequest request, StreamObserver<GetLatestNewsResponse> responseObserver) {
        int limit = Math.min(request.getLimit() > 0 ? request.getLimit() : 5, 50);
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NewsEntity> page = newsRepository.findAllByOrderByCreatedAtDesc(pageRequest);

        List<News> newsList = page.getContent().stream()
                .map(this::toProto)
                .collect(Collectors.toList());

        GetLatestNewsResponse response = GetLatestNewsResponse.newBuilder()
                .addAllNews(newsList)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getAllNews(GetAllNewsRequest request, StreamObserver<GetAllNewsResponse> responseObserver) {
        int pageNumber = Math.max(0, request.getPage());
        int pageSize = Math.min(
                request.getPageSize() > 0 ? request.getPageSize() : 10,
                50
        );

        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NewsEntity> page = newsRepository.findAllByOrderByCreatedAtDesc(pageRequest); // ← теперь нет конфликта

        List<News> newsList = page.getContent().stream()
                .map(this::toProto)
                .collect(Collectors.toList());

        GetAllNewsResponse response = GetAllNewsResponse.newBuilder()
                .addAllNews(newsList)
                .setTotalCount((int) page.getTotalElements())
                .setHasMore(!page.isLast())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getNewsById(GetNewsByIdRequest request, StreamObserver<GetNewsByIdResponse> responseObserver) {
        Long id = request.getId();
        NewsEntity entity = newsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entity with id: " + id + "not found"));

        News proto = toProto(entity);

        GetNewsByIdResponse response = GetNewsByIdResponse.newBuilder()
                        .setNews(proto)
                        .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateNewsById(UpdateNewsByIdRequest request, StreamObserver<UpdateNewsByIdResponse> responseObserver) {
        Long id = request.getId();
        NewsEntity news = newsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("News not found with id: " + id));

        String newTitle = request.getTitle();
        if (!news.getTitle().equals(newTitle) && newsRepository.existsByTitle(newTitle)) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription("News with title '" + newTitle + "' already exists")
                            .asRuntimeException()
            );
            return;
        }

        news.setTitle(newTitle);
        news.setContent(request.getContent());
        news.setAuthor(request.getAuthor());
        if(!request.getImageData().isEmpty())
        {
            String newImageUrl = minioService.updateImage(
                    news.getImageUrl(),
                    request.getImageData().toByteArray(),
                    request.getImageContentType(),
                    request.getImageFilename()
            );
            news.setImageUrl(newImageUrl);
        }
        news.setIsNew(request.getIsNew());
        news.setUpdatedAt(Instant.now());

        NewsEntity saved = newsRepository.save(news);

        News proto = toProto(saved);
        UpdateNewsByIdResponse response = UpdateNewsByIdResponse.newBuilder()
                .setNews(proto)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void deleteNewsById(DeleteNewsByIdRequest request, StreamObserver<DeleteNewsByIdResponse> responseObserver) {
        Long id = request.getId();
        NewsEntity news = newsRepository.findById(id)
                .orElse(null);

        if (news == null) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("News not found with id: " + id)
                            .asRuntimeException()
            );
            return;
        }

        // Удаляем картинку из MinIO, если она есть
        if (news.getImageUrl() != null && !news.getImageUrl().isEmpty()) {
            minioService.deleteImage(news.getImageUrl());
        }

        newsRepository.deleteById(id);

        DeleteNewsByIdResponse response = DeleteNewsByIdResponse.newBuilder()
                .setSuccess(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private News toProto(NewsEntity e) {
        News.Builder builder = News.newBuilder()
                .setId(e.getId())
                .setTitle(e.getTitle())
                .setContent(e.getContent())
                .setAuthor(e.getAuthor())
                .setImageUrl(e.getImageUrl())
                .setIsNew(e.getIsNew())
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(e.getCreatedAt().getEpochSecond())
                        .setNanos(e.getCreatedAt().getNano())
                        .build());
        if (e.getUpdatedAt() != null) {
            builder.setUpdatedAt(Timestamp.newBuilder()
                    .setSeconds(e.getUpdatedAt().getEpochSecond())
                    .setNanos(e.getUpdatedAt().getNano())
                    .build());
        }

        return builder.build();
    }
}
