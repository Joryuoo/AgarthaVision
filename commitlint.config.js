export default {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-enum": [2, "always", [
      "capture",
      "inference",
      "dashboard",
      "reports",
      "theme",
      "core",
      "data",
      "ci",
      "docs",
    ]],
  },
};