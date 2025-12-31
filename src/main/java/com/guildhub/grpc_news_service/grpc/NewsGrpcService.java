package com.guildhub.grpc_news_service.grpc;

import com.guildhub.grpc_news_service.Entity.NewsEntity;
import com.guildhub.grpc_news_service.Repository.NewsRepository;
import com.guildhub.newsservice.grpc.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.stream.Collectors;

@GrpcService
public class NewsGrpcService extends NewsServiceGrpc.NewsServiceImplBase {

    private final NewsRepository newsRepository;

    public NewsGrpcService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
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
        int pageNumber = Math.max(0, request.getPage()); // ← новое имя
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

    private News toProto(NewsEntity e) {
        return News.newBuilder()
                .setId(e.getId())
                .setTitle(e.getTitle())
                .setContent(e.getContent())
                .setAuthor(e.getAuthor())
                .setImageUrl(e.getImageUrl())
                .setIsNew(e.getIsNew())
                .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(e.getCreatedAt().getEpochSecond())
                        .build())
                .build();
    }
}
