import { z } from "zod";

export const aiGenerateSchema = z.object({
  count: z.number({ message: "请输入题目数量" }).int("题目数量必须为整数").min(1, "题目数量至少 1 道").max(20, "题目数量最多 20 道"),
  subject: z.string({ message: "请输入科目" }).trim().min(1, "请输入科目"),
  difficulty: z.enum(["EASY", "MEDIUM", "HARD"]).optional(),
  conceptTags: z.array(z.string()).optional()
});
