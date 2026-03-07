import { defineStore } from "pinia";
import { toErrorMessage } from "../../../services/error-message";
import { listTeacherClassrooms, listTeacherStudents } from "../api/teacher.service";
import type { TeacherClassroomVO, TeacherStudentVO } from "../../../services/contracts";

interface ClassroomState {
  classrooms: TeacherClassroomVO[];
  classroomsLoading: boolean;
  classroomsError: string;

  students: TeacherStudentVO[];
  studentsLoading: boolean;
  studentsError: string;
}

export const useClassroomStore = defineStore("teacher-classroom", {
  state: (): ClassroomState => ({
    classrooms: [],
    classroomsLoading: false,
    classroomsError: "",

    students: [],
    studentsLoading: false,
    studentsError: ""
  }),
  actions: {
    async loadClassrooms(): Promise<void> {
      this.classroomsLoading = true;
      this.classroomsError = "";
      try {
        this.classrooms = await listTeacherClassrooms();
      } catch (error) {
        this.classroomsError = toErrorMessage(error, "加载班级列表失败");
      } finally {
        this.classroomsLoading = false;
      }
    },

    async loadStudents(): Promise<void> {
      this.studentsLoading = true;
      this.studentsError = "";
      try {
        this.students = await listTeacherStudents();
      } catch (error) {
        this.studentsError = toErrorMessage(error, "加载学生列表失败");
      } finally {
        this.studentsLoading = false;
      }
    }
  }
});
