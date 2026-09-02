# 阶段 4 Task 2 总结：三张新表 + 读写（2026-09-02）

> 今日状态：Task 2 完成，全量 67 个测试绿，已提交

## 今日范围
- `schema.sql`
- `Database.java`
- `entity`×3
- `mapper`×3
- `InternshipItem/ProfileItem/StudyProgressItem` 读模型×3
- `ItemRepository.java`
- `ExtractedProfile.java`
- `AppBeans.java`
- `ItemRepositoryTest.java`

## 今日知识点

### 1. 三张新表设计

- internships 多行表 (company/position NOT NULL status默认'new'预留)
- profile 单行表 (id固定为1 @TableId 用 INPUT 不是 AUTO，写操作包括删除)
- study_progress 多行表 (study_date/content NOT NULL)

### 2. 实体：@TableId 的 AUTO vs INPUT

- AUTO是数据库自增生成的id，配普通表(internships/study_progress)
- INPUT是手动填的id，配单行表(如profile固定setId(1L))
- 单行表配AUTO可能插入失败或者使单行表不只单行

### 3. 字段名映射陷阱

- java代码里sourceMessageId在数据库中会被MyBatis——Plus映射成source_message_id
- 这类bug编译不报错，只能运行测试时通过断言键值发现

### 4. 读模型 vs 实体

- 实体是数据库行对java对象的直接映射，字段类型全都是String
- 但是业务层用的是读模型(record类型的类)，字段类型不一定用String，比如StudyProgressItem.studyDate用的是LocalDate
- 所以，repo会在数据库出口处做转换(LocalDate.parse)，这样数据库层用简单类型String，业务层用丰富类型

### 5. LambdaQueryWrapper 条件查询

- 是一个条件包装器，把查询条件打包成一个类
- ge/le代表>=和<=，是闭区间，gt/lt代表>和<，是开区间，本项目用到闭区间，周复盘要包括今天
- orderByAsc用来排序，方法引用(StudyProgress::getStudyDate)类型安全，编译期报错

### 6. Stream map 转换

- selectList函数返回的是List<实体> 
- 需要selectList(...).stream().map(实体 -> 读模型).toList(); 逐个元素转换，把实体字段搬到读模型去，返回List<StudyProgressItem>
- 5 + 4(6中可能用) + 6 -> 查询

### 7. repo 方法与表形状

- 对于多行表(internships/study_progress)，有增删查方法，没写改是因为实际用不上
- 对于单行表(profile)只有覆盖写setProfile+读profile方法
- setProfile = deleteById(1L) + insert(先删后插保证单行)
- 插入时接收裸参数(数据来自LLM抽取)，查询返回读模型(给上层消费)

### 8. 仿写方法论

- 找参照函数从三个维度找：
- 方法体骨架结构
- 参数风格 比如setSemester(裸参数)
- 返回值 比如insert()里return id

## 文件与方法链路

插入链路：repo.insertInternship(裸参数)方法内部new Internship对象(i) + setter + internshipMapper.insert(i)，最后return i.getId()
查询链路：studyProgressMapper.selectList(wrapper.ge/le)得到List<实体>，然后.stream().map(实体 -> 读模型).toList()最后返回一个List<StudyProgressItem>
单行表写：setProfile函数内先执行deleteById(1L)，然后插入流程和插入链路相同

## 今天踩坑记录

- 1.Profile用错IdType.AUTO(应为INPUT)
- 2.StudyProgressMapper泛型写成BaseMapper<StudyProgressMapper>,Mapper执行操作时查找这个表名找不到
- 3.setProfile参数列表和测试不匹配(裸参数 vs ExtractedProfile)；

## 下一步

- Task 3：internship 摄入 + 删除支持（仿写 ExtractedInternship + Prompts.EXTRACT 增补 + engine 分支 + 删除）
