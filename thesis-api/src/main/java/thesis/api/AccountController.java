package thesis.api;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import thesis.core.article.Article;
import thesis.core.article.command.CommandCommonQuery;
import thesis.core.article.repository.ArticleRepository;
import thesis.core.article.service.ArticleService;
import thesis.core.news.account.Account;
import thesis.core.news.account.repository.AccountRepository;
import thesis.core.news.command.*;
import thesis.core.news.member.Member;
import thesis.core.news.member.repository.MemberRepository;
import thesis.core.news.report.news_report.NewsReport;
import thesis.core.news.report.news_report.repository.NewsReportRepository;
import thesis.core.news.response.AccountResponse;
import thesis.core.news.response.OtpResponse;
import thesis.core.news.response.UserResponse;
import thesis.core.news.role.Role;
import thesis.core.news.role.repository.RoleRepository;
import thesis.core.search_engine.dto.SearchEngineResult;
import thesis.utils.constant.DEFAULT_ROLE;
import thesis.utils.constant.MAIL_SENDER_TYPE;
import thesis.utils.constant.REPORT_TYPE;
import thesis.utils.dto.ResponseDTO;
import thesis.utils.helper.PageHelper;
import thesis.utils.helper.PasswordHelper;
import thesis.utils.mail.CommandMail;
import thesis.utils.mail.MailSender;
import thesis.utils.otp.OtpCacheService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
public class AccountController {
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.of("+07:00");

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private MailSender mailSender;
    @Autowired
    private OtpCacheService otpCacheService;
    @Autowired
    private NewsReportRepository newsReportRepository;

    @RequestMapping(method = RequestMethod.POST, value = "/login")
    public ResponseEntity<ResponseDTO<?>> login(@RequestBody CommandLogin command) {
        try {
            Account account = accountRepository.findOne(new Document("email", command.getEmail()), new Document())
                    .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));
            if (!PasswordHelper.checkPassword(command.getPassword(), account.getPassword()))
                throw new Exception("Mật khẩu không đúng");

            Member member = memberRepository.findOne(new Document("_id", new ObjectId(account.getMemberId())), new Document())
                    .orElseThrow(() -> new Exception("Dữ liệu người dùng không tồn tại"));

            if (BooleanUtils.isNotTrue(member.getIsActive()))
                throw new Exception("Tài khoản đã bị khóa, vui lòng liên hệ admin");

            Role role = roleRepository.findOne(new Document("_id", new ObjectId(member.getRoleId())), new Document())
                    .orElseThrow(() -> new Exception("Quyền người dùng không tồn tại"));

            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(AccountResponse.builder()
                            .account(account)
                            .member(member)
                            .role(role)
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/registry")
    public ResponseEntity<ResponseDTO<?>> registry(@RequestBody CommandLogin command) {
        try {
            Optional<Account> existedAccount = accountRepository.findOne(new Document("email", command.getEmail()), new Document());
            if (existedAccount.isPresent())
                throw new Exception("Email đã tồn tại, vui lòng thử email khác");

            Member member = Member.builder()
                    .fullName(command.getEmail().split("@")[0])
                    .roleId(DEFAULT_ROLE.MEMBER.getRoleId())
                    .isActive(true)
                    .email(command.getEmail())
                    .build();
            memberRepository.insert(member);

            Account account = Account.builder()
                    .email(command.getEmail())
                    .password(PasswordHelper.hashPassword(command.getPassword()))
                    .memberId(member.getId().toString())
                    .build();

            accountRepository.insert(account);

            Role role = roleRepository.findOne(new Document("_id", new ObjectId(member.getRoleId())), new Document())
                    .orElseThrow(() -> new Exception("Quyền người dùng không tồn tại"));

            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(AccountResponse.builder()
                            .account(account)
                            .member(member)
                            .role(role)
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/post")
    public ResponseEntity<ResponseDTO<?>> post(@RequestBody CommandPost command) {
        try {
            Member member = memberRepository.findOne(new Document("_id", new ObjectId(command.getMemberId())), new Document())
                    .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));

            Role role = roleRepository.findOne(new Document("_id", new ObjectId(member.getRoleId())), new Document())
                    .orElseThrow(() -> new Exception("Quyền người dùng không tồn tại"));

            if (!role.getNumberValue().equals(DEFAULT_ROLE.AUTHOR.getNumberValue()))
                throw new Exception("Người dùng không có quyền đăng bài");
            if (StringUtils.isBlank(command.getTitle()))
                throw new Exception("Tiêu đề không được để trống");
            if (StringUtils.isBlank(command.getDescription()))
                throw new Exception("Mô tả đề không được để trống");
            if (StringUtils.isBlank(command.getContent()))
                throw new Exception("Nội dung không được để trống");
            if (StringUtils.isBlank(command.getTopic()))
                throw new Exception("Chủ đề không được để trống");

            Article article = Article.builder()
                    .authors(Collections.singletonList(member.getFullName()))
                    .title(command.getTitle())
                    .content(command.getContent())
                    .description(command.getDescription())
                    .topics(Collections.singletonList(command.getTopic()))
                    .labels(CollectionUtils.isNotEmpty(command.getLabels()) ? command.getLabels() : new ArrayList<>())
                    .images(command.getImages())
                    .publicationDate(System.currentTimeMillis() / 1000)
                    .build();
            articleService.add(article);

            if (CollectionUtils.isEmpty(member.getPublishedArticles()))
                member.setPublishedArticles(new ArrayList<>());
            member.getPublishedArticles().add(article.getId().toHexString());

            memberRepository.update(new Document("_id", member.getId()), new Document("publishedArticles", member.getPublishedArticles()));

            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(SearchEngineResult.builder()
                            .articles(Collections.singletonList(article))
                            .build())
                    .message("Đăng bài thành công")
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/save")
    public ResponseEntity<ResponseDTO<?>> save(@RequestBody CommandSave command) {
        try {
            Article article = articleRepository.findOne(new Document("_id", new ObjectId(command.getArticleId())), new Document())
                    .orElseThrow(() -> new Exception("Bài báo không tồn tại"));

            if ("view".equals(command.getType())) {
                processReportedLabel(Set.of(article.getId().toHexString()));
            }
            if (StringUtils.isBlank(command.getMemberId()))
                return new ResponseEntity<>(ResponseDTO.builder().build(), HttpStatus.OK);

            Member member = memberRepository.findOne(new Document("_id", new ObjectId(command.getMemberId())), new Document())
                    .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));

            if (CollectionUtils.isEmpty(member.getSavedArticles()))
                member.setSavedArticles(new ArrayList<>());
            if (CollectionUtils.isEmpty(member.getViewedArticles()))
                member.setViewedArticles(new ArrayList<>());
            String message = "";
            switch (command.getType()) {
                case "save" -> {
                    if (!member.getSavedArticles().contains(article.getId().toString())) {
                        member.getSavedArticles().add(article.getId().toHexString());
                        memberRepository.update(new Document("_id", member.getId()), new Document("savedArticles", member.getSavedArticles()));
                    }
                    message = "Đã lưu bài viết";
                }
                case "unsave" -> {
                    if (!member.getSavedArticles().remove(article.getId().toHexString()))
                        throw new Exception("Không thể gỡ bài viết hoặc bài viết không tồn tại trong danh sách đã lưu");
                    memberRepository.update(new Document("_id", member.getId()), new Document("savedArticles", member.getSavedArticles()));
                }
                case "view" -> {
                    if (!member.getSavedArticles().contains(article.getId().toString())) {
                        member.getViewedArticles().add(article.getId().toHexString());
                        memberRepository.update(new Document("_id", member.getId()), new Document("viewedArticles", member.getViewedArticles()));
                    }
                }
            }

            Account account = accountRepository.findOne(new Document("memberId", member.getId().toHexString()), new Document())
                    .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));

            Role role = roleRepository.findOne(new Document("_id", new ObjectId(member.getRoleId())), new Document())
                    .orElseThrow(() -> new Exception("Quyền người dùng không tồn tại"));


            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(AccountResponse.builder()
                            .account(account)
                            .member(member)
                            .role(role)
                            .build())
                    .message(message)
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/get_articles")
    public ResponseEntity<ResponseDTO<?>> getArticleByMember(@RequestBody CommandGetArticleByMember command) {
        try {
            List<String> articleIds = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(command.getArticleIds())) {
                articleIds = command.getArticleIds();
            } else {
                Member member = memberRepository.findOne(new Document("_id", new ObjectId(command.getMemberId())), new Document())
                        .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));
                switch (command.getType()) {
                    case "saved" -> articleIds = member.getSavedArticles();
                    case "published" -> articleIds = member.getPublishedArticles();
                    case "viewed" -> articleIds = member.getViewedArticles();
                }
            }
            List<Article> articles = CollectionUtils.isNotEmpty(articleIds)
                    ? articleService.get(CommandCommonQuery.builder()
                    .articleIds(new HashSet<>(articleIds))
                    .isDescPublicationDate(true)
                    .page(command.getPage())
                    .size(command.getSize())
                    .build())
                    : new ArrayList<>();

            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(SearchEngineResult.builder()
                            .articles(articles)
                            .total((long) articleIds.size())
                            .totalPage((articleIds.size() + command.getSize() - 1) / command.getSize())
                            .page(command.getPage())
                            .size(command.getSize())
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/random_articles")
    public ResponseEntity<ResponseDTO<?>> randomArticles(@RequestBody CommandGetArticleByMember command) {
        try {
            long totalArticle = articleService.count(CommandCommonQuery.builder().build()).orElseThrow();
            int totalPage = (int) ((totalArticle + command.getSize() - 1) / command.getSize());

            List<Article> articles = articleService.get(CommandCommonQuery.builder()
                    .page(new Random().nextInt(totalPage - 1))
                    .size(command.getSize())
                    .build());

            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(SearchEngineResult.builder()
                            .articles(articles)
                            .size(command.getSize())
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/send-otp")
    public ResponseEntity<ResponseDTO<?>> sendOtp(@RequestBody CommandChangePassword command) {
        try {
            if (StringUtils.isBlank(command.getEmail()))
                throw new Exception("Email không hợp lệ");
            if (accountRepository.findOne(new Document("email", command.getEmail()), new Document()).isEmpty())
                throw new Exception("Tài khoản không tồn tại");
            long waitTime = otpCacheService.canResend(command.getEmail());
            if (waitTime > 0) {
                throw new Exception("Vui lòng gửi lại sau " + waitTime + "s");
            }
            String otp = otpCacheService.generateOTP(4);
            try {
                otpCacheService.storeOtp(command.getEmail(), otp);
                mailSender.send(CommandMail.builder()
                        .to(command.getEmail())
                        .subject("Yêu cầu đổi mật khẩu")
                        .otp(otp)
                        .mailSenderType(MAIL_SENDER_TYPE.OTP)
                        .build());
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new Exception("Gửi otp thất bại, vui lòng thử lại");
            }
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(OtpResponse.builder()
                            .email(command.getEmail())
                            .isSuccess(true)
                            .message("Gửi otp thành công")
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/verify-otp")
    public ResponseEntity<ResponseDTO<?>> verifyOtp(@RequestBody CommandChangePassword command) {
        try {
            if (StringUtils.isBlank(command.getEmail()))
                throw new Exception("Email không hợp lệ");
            if (accountRepository.findOne(new Document("email", command.getEmail()), new Document()).isEmpty())
                throw new Exception("Tài khoản không tồn tại");
            if (StringUtils.isBlank(command.getOtp()))
                throw new Exception("Otp không hợp lệ");
            if (!otpCacheService.validateOtp(command.getEmail(), command.getOtp()))
                throw new Exception("Otp không hợp lệ");
            otpCacheService.clearOtp(command.getEmail());
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(OtpResponse.builder()
                            .email(command.getEmail())
                            .isSuccess(true)
                            .message("Xác thực otp thành công")
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/reset-password")
    public ResponseEntity<ResponseDTO<?>> resetPassword(@RequestBody CommandChangePassword command) {
        try {
            if (StringUtils.isBlank(command.getEmail()))
                throw new Exception("Email không hợp lệ");
            if (StringUtils.isBlank(command.getPassword()))
                throw new Exception("Mật khẩu không hợp lệ");
            Optional<Account> accountOptional = accountRepository.findOne(new Document("email", command.getEmail()), new Document());
            if (accountOptional.isEmpty())
                throw new Exception("Tài khoản không tồn tại");
            accountRepository.update(new Document("_id", accountOptional.get().getId()), new Document("password", PasswordHelper.hashPassword(command.getPassword())));
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(OtpResponse.builder()
                            .email(command.getEmail())
                            .isSuccess(true)
                            .message("Đổi mật khẩu thành công")
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/change-password")
    public ResponseEntity<ResponseDTO<?>> changePassword(@RequestBody CommandChangePassword command) {
        try {
            if (StringUtils.isBlank(command.getEmail()))
                throw new Exception("Email không hợp lệ");
            if (StringUtils.isBlank(command.getPassword()))
                throw new Exception("Mật khẩu không hợp lệ");
            Optional<Account> accountOptional = accountRepository.findOne(new Document("email", command.getEmail()), new Document());
            if (accountOptional.isEmpty())
                throw new Exception("Tài khoản không tồn tại");
            if (!PasswordHelper.checkPassword(command.getOldPassword(), accountOptional.get().getPassword()))
                throw new Exception("Mật khẩu không đúng");
            accountRepository.update(new Document("_id", accountOptional.get().getId()), new Document("password", PasswordHelper.hashPassword(command.getPassword())));
            mailSender.send(CommandMail.builder()
                    .to(command.getEmail())
                    .subject("Cập nhật mật khẩu thành công")
                    .mailSenderType(MAIL_SENDER_TYPE.PASSWORD_CHANGED)
                    .build());
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(OtpResponse.builder()
                            .email(command.getEmail())
                            .isSuccess(true)
                            .message("Đổi mật khẩu thành công")
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/list")
    public ResponseEntity<ResponseDTO<?>> getUsers(@RequestBody CommandGetListUser command) {
        try {
            Member member = memberRepository.findOne(new Document("_id", new ObjectId(command.getMemberId())), new Document())
                    .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));

            Role role = roleRepository.findOne(new Document("_id", new ObjectId(member.getRoleId())), new Document())
                    .orElseThrow(() -> new Exception("Quyền người dùng không tồn tại"));

            if (!role.getNumberValue().equals(DEFAULT_ROLE.ADMIN.getNumberValue()))
                throw new Exception("Người dùng không có quyền");
            Map<String, Object> queryMember = new HashMap<>();
            queryMember.put("roleId", new Document("$ne", DEFAULT_ROLE.ADMIN.getRoleId()));

            if (StringUtils.isNotBlank(command.getKeyword())) {
                Map<String, Object> textSearch = new HashMap<>();
                Map<String, Object> search = new HashMap<>();
                search.put("$search", command.getKeyword());
                textSearch.put("$text", search);

                Map<String, Object> regex = new HashMap<>();
                regex.put("$regex", Pattern.compile(command.getKeyword(), Pattern.CASE_INSENSITIVE));

                Map<String, Object> queryEmail = new HashMap<>();
                queryEmail.put("email", regex);

                Map<String, Object> queryName = new HashMap<>();
                queryName.put("fullName", regex);

                queryMember.put("$or", Arrays.asList(queryEmail, queryName));
            }

            Long totalUser = memberRepository.count(new Document(queryMember)).orElse(0L);
            List<Member> members = memberRepository.find(queryMember,
                    new Document("createdDate", -1),
                    new Document(),
                    PageHelper.getSkip(command.getPage(), command.getSize()),
                    command.getSize());
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(UserResponse.builder()
                            .keyword(command.getKeyword())
                            .members(members.stream().map(mem -> UserResponse.MemberResponse.builder()
                                    .id(mem.getId().toString())
                                    .fullName(mem.getFullName())
                                    .email(mem.getEmail())
                                    .createdDate(mem.getCreatedDate())
                                    .role(DEFAULT_ROLE.getRoleById(mem.getRoleId()))
                                    .roleValue(DEFAULT_ROLE.getRoleNum(mem.getRoleId()))
                                    .isActive(mem.getIsActive())
                                    .build()).collect(Collectors.toList()))
                            .page(command.getPage())
                            .size(command.getSize())
                            .total(totalUser)
                            .totalPage(PageHelper.getTotalPage(totalUser, command.getSize()))
                            .build())
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/update")
    public ResponseEntity<ResponseDTO<?>> updateUser(@RequestBody CommandUpdateUser command) {
        try {
            if (StringUtils.isBlank(command.getUpdateMemberId()))
                throw new Exception("Vui lòng thêm người dùng cần cập nhật");
            Map<String, Object> updateQuery = new HashMap<>();

            Member updateMember = memberRepository.findOne(new Document("_id", new ObjectId(command.getUpdateMemberId())), new Document())
                    .orElseThrow(() -> new Exception("Thông tin người dùng không tồn tại"));

            if (command.getRoleLevel() != null || command.getIsBlocked() != null) {
                Member adminMember = memberRepository.findOne(new Document("_id", new ObjectId(command.getMemberId())), new Document())
                        .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));

                Role role = roleRepository.findOne(new Document("_id", new ObjectId(adminMember.getRoleId())), new Document())
                        .orElseThrow(() -> new Exception("Quyền người dùng không tồn tại"));

                if (!role.getNumberValue().equals(DEFAULT_ROLE.ADMIN.getNumberValue()))
                    throw new Exception("Người dùng không có quyền");


                if (command.getRoleLevel() != null) {
                    if (Objects.equals(DEFAULT_ROLE.getRoleIdByNumberValue(command.getRoleLevel()), updateMember.getRoleId()))
                        throw new Exception("Cập nhật quyền không thành công");
                    updateMember.setRoleId(DEFAULT_ROLE.getRoleIdByNumberValue(command.getRoleLevel()));
                    updateQuery.put("roleId", updateMember.getRoleId());
                }

                if (command.getIsBlocked() != null) {
                    updateMember.setIsActive(!command.getIsBlocked());
                    updateQuery.put("isActive", updateMember.getIsActive());
                }
            } else {
                if (!updateMember.getId().toString().equals(command.getMemberId()))
                    throw new Exception("Vui lòng đăng nhập và thử lại");
                if (StringUtils.isNotBlank(command.getFullName())) {
                    updateMember.setFullName(command.getFullName());
                    updateQuery.put("fullName", updateMember.getFullName());
                }
            }

            if (MapUtils.isEmpty(updateQuery))
                throw new Exception("Không có thông tin cần cập nhật, vui lòng kiểm tra lại");

            memberRepository.update(new Document("_id", updateMember.getId()), updateQuery);

            Account account = accountRepository.findOne(new Document("memberId", updateMember.getId().toHexString()), new Document())
                    .orElseThrow(() -> new Exception("Tài khoản không tồn tại"));

            Role role = roleRepository.findOne(new Document("_id", new ObjectId(updateMember.getRoleId())), new Document())
                    .orElseThrow(() -> new Exception("Quyền người dùng không tồn tại"));

            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(HttpStatus.OK.value())
                    .data(AccountResponse.builder()
                            .account(account)
                            .member(updateMember)
                            .role(role)
                            .build())
                    .message("Cập nhật thông tin thành công")
                    .build(), HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ResponseDTO.builder()
                    .statusCode(-1)
                    .message(ex.getMessage())
                    .build(), HttpStatus.OK);
        }
    }

    static int duplicateSave = 0;

    private void processReportedLabel(Set<String> labels) {
        if (duplicateSave % 2 != 0) {
            duplicateSave = 0;
            return;
        }
        duplicateSave++;
        long currentTime = System.currentTimeMillis() / 1000;
        long startOfDay = LocalDateTime.ofEpochSecond(currentTime, 0, ZONE_OFFSET).toLocalDate()
                .atStartOfDay().toEpochSecond(ZONE_OFFSET);

        if (CollectionUtils.isEmpty(labels))
            return;

        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("reportDate", startOfDay);
        queryMap.put("reportType", REPORT_TYPE.VIEW.getValue());
        Map<String, Object> sortMap = new HashMap<>();
        sortMap.put("updatedDate", -1);
        sortMap.put("createdDate", -1);

        NewsReport newsReport = newsReportRepository.findOne(queryMap, sortMap).orElse(null);
        if (newsReport == null) {
            newsReport = NewsReport.builder()
                    .reportDate(startOfDay)
                    .reportType(REPORT_TYPE.VIEW.getValue())
                    .labelCounts(new HashMap<>())
                    .build();
            newsReportRepository.insert(newsReport);
        }

        for (String label : labels) {
            newsReport.getLabelCounts().compute(label, (k, v) -> (v == null) ? 1 : v + 1);
        }

        newsReportRepository.update(new Document("_id", newsReport.getId()), new Document("labelCounts", newsReport.getLabelCounts()));
    }
}
