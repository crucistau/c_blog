package xyz.crucistau.constants;

/**
 * 
 * <p>
 * 创建时间：2026/07/20
 * ES 索引与高亮常量类
 */
public class EsIndexConst {

    /**
     * 文章索引
     */
    public static final String ARTICLE_INDEX = "blog_article";

    /**
     * 留言索引
     */
    public static final String LEAVE_WORD_INDEX = "blog_leave_word";

    /**
     * 标签索引
     */
    public static final String TAG_INDEX = "blog_tag";

    /**
     * 分类索引
     */
    public static final String CATEGORY_INDEX = "blog_category";

    /**
     * 高亮前置标签
     */
    public static final String HIGHLIGHT_PRE_TAG = "<em class=\"highlight\">";

    /**
     * 高亮后置标签
     */
    public static final String HIGHLIGHT_POST_TAG = "</em>";

    /**
     * 正文摘要截断长度
     */
    public static final int CONTENT_SNIPPET_LENGTH = 200;
}
