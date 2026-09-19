package com.example.sevice;

import java.util.List;

public interface EmbeddingService {

    List<Float> embed(String text);

    List<Float> embedForSearch(String text);
}