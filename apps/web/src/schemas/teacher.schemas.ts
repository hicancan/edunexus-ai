import { z } from "zod";

const optionalUuid = z.union([z.literal(""), z.uuid("请输入合法的 UUID")]);

export const teacherPlanSchema = z.object({
  topic: z.string({ message: "请输入教案主题" }).trim().min(2, "教案主题至少 2 个字符"),
  gradeLevel: z.string({ message: "请输入适用年级" }).trim().min(1, "请输入适用年级"),
  durationMins: z.number({ message: "请输入课时长度" }).int("课时必须为整数").min(1, "课时至少 1 分钟").max(240, "课时不能超过 240 分钟")
});

export const teacherSuggestionSchema = z.object({
  studentId: z.uuid("学生 ID 必须是合法 UUID"),
  questionId: optionalUuid,
  knowledgePoint: z.string().trim().max(100, "知识点最多 100 个字符").optional(),
  suggestion: z.string({ message: "请输入建议内容" }).trim().min(5, "建议内容至少 5 个字符")
});
