package xyz.kuailemao.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.kuailemao.domain.dto.EsSyncMessage;
import xyz.kuailemao.domain.dto.EsSyncMessage.EntityType;
import xyz.kuailemao.domain.dto.EsSyncMessage.SyncType;
import xyz.kuailemao.search.builder.DocumentBuilder;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link EsSyncListener} 单测：验证按 {@link EntityType}/{@link SyncType} 正确分发
 * 到对应 {@link DocumentBuilder}，以及未知 entityType 安全跳过。
 *
 * <p>纯 Mockito，不启动 Spring 上下文 —— 仅验证路由逻辑。</p>
 *
 * @author kuailemao
 */
class EsSyncListenerTest {

    private DocumentBuilder articleBuilder;
    private DocumentBuilder leaveWordBuilder;
    private EsSyncListener listener;

    @BeforeEach
    void setUp() {
        articleBuilder = mock(DocumentBuilder.class);
        leaveWordBuilder = mock(DocumentBuilder.class);
        when(articleBuilder.type()).thenReturn(EntityType.ARTICLE);
        when(leaveWordBuilder.type()).thenReturn(EntityType.LEAVE_WORD);
        listener = new EsSyncListener(List.of(articleBuilder, leaveWordBuilder));
    }

    @Test
    void whenSaveArticle_thenUpsertOnArticleBuilder() {
        EsSyncMessage msg = EsSyncMessage.builder()
                .entityType(EntityType.ARTICLE)
                .id(100L)
                .syncType(SyncType.SAVE)
                .build();

        listener.handle(msg);

        verify(articleBuilder).upsert(100L);
        verifyNoInteractions(leaveWordBuilder);
    }

    @Test
    void whenDeleteLeaveWord_thenDeleteOnLeaveWordBuilder() {
        EsSyncMessage msg = EsSyncMessage.builder()
                .entityType(EntityType.LEAVE_WORD)
                .id(200L)
                .syncType(SyncType.DELETE)
                .build();

        listener.handle(msg);

        verify(leaveWordBuilder).delete(200L);
        verifyNoInteractions(articleBuilder);
    }

    @Test
    void whenUnknownEntityType_thenNoExceptionAndNoInteraction() {
        EsSyncMessage msg = EsSyncMessage.builder()
                .entityType(EntityType.TAG)
                .id(300L)
                .syncType(SyncType.UPDATE)
                .build();

        // 无对应 builder，应安全跳过，不抛异常、不调用任何 builder
        listener.handle(msg);

        verifyNoInteractions(articleBuilder, leaveWordBuilder);
    }
}
