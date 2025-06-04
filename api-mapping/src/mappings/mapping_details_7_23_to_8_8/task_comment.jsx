export const task_comment = [
	{
		origin: {
			path: "/task/{id}/comment",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Not yet possible in Camunda 8.8. No comments for tasks yet.
			</div>
		),
	},
	{
		origin: {
			path: "/task/{id}/comment/create",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Not yet possible in Camunda 8.8. No comments for tasks yet.
			</div>
		),
	},
	{
		origin: {
			path: "/task/{id}/comment/{commentId}",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Not yet possible in Camunda 8.8. No comments for tasks yet.
			</div>
		),
	},
];
