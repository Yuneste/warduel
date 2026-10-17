package com.warduel.warduel.model;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.socket.WebSocketSession;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@ToString(exclude = {"session", "leaderboardToken"})
public class Player {

    private String playerId;
    private volatile String displayName;
    private WebSocketSession session;
    private final AtomicInteger score = new AtomicInteger(0);
    private final AtomicInteger currentQuestionIndex = new AtomicInteger(0);
    private List<Question> questions = new ArrayList<>();
    private final Set<Integer> answeredQuestions = ConcurrentHashMap.newKeySet();

    // Post-game stats: every answer of the current round and when the current question appeared
    private final List<AnswerRecord> answerLog = new CopyOnWriteArrayList<>();
    private volatile long questionShownAtMillis;

    // Random device token for the leaderboard (only its hash is ever stored); null = anonymous
    private volatile String leaderboardToken;

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
        this.answeredQuestions.clear();
        this.answerLog.clear();
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

    public boolean hasAnsweredQuestion(int questionIndex) {
        return answeredQuestions.contains(questionIndex);
    }

    public void markQuestionAnswered(int questionIndex) {
        answeredQuestions.add(questionIndex);
    }
}