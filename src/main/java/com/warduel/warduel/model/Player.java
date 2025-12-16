package com.warduel.warduel.model;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.socket.WebSocketSession;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString(exclude = "session")
public class Player {

    private String playerId;
    private volatile String displayName;
    private WebSocketSession session;
    private final AtomicInteger score = new AtomicInteger(0);
    private final AtomicInteger currentQuestionIndex = new AtomicInteger(0);
    private List<Question> questions = new ArrayList<>();

    public Player(String playerId, WebSocketSession session, String displayName) {
        this.playerId = playerId;
        this.session = session;
        this.displayName = displayName;
    }

    public void incrementScore() {
        this.score.incrementAndGet();
    }

    public void resetScore() {
        this.score.set(0);
    }

    public int getScore() {
        return this.score.get();
    }

    public int getCurrentQuestionIndex() {
        return this.currentQuestionIndex.get();
    }

    public void nextQuestion() {
        this.currentQuestionIndex.incrementAndGet();
    }

    public void resetQuestionIndex() {
        this.currentQuestionIndex.set(0);
    }

    public void setQuestions(List<Question> questions) {
        this.questions = new ArrayList<>(questions);
    }

    public Question getCurrentQuestion() {
        int index = this.getCurrentQuestionIndex();
        if(index < questions.size()) {
            return questions.get(index);
        }
        return null;
    }
}